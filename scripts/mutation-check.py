r"""Mutation checks: break one thing on purpose, run the tests, and require a test to go red.

    python scripts/mutation-check.py --list                  # the standing checks in scripts/mutants.json
    python scripts/mutation-check.py                         # run all of them
    python scripts/mutation-check.py feed-signature          # run named ones
    python scripts/mutation-check.py --jobs 4                # run them on 4 throwaway git worktrees at once
    python scripts/mutation-check.py --file F --find A --replace B --task :core:test   # one ad-hoc check

Each result is one of:
  CAUGHT    a test failed with the bug in place (good)
  SURVIVED  every test still passed (the tests do not guard that behaviour; fix the tests)
  INVALID   the broken code did not compile, so it proved nothing (fix the mutant)
  ERROR     the build failed in some other way

The file is always put back, even on Ctrl-C or a crash; a backup is kept in build/mutation-backup/ until the run ends
and is restored on the next start if a run was killed. Exit code 0 only if every check was CAUGHT.
With --jobs N (default: min(4, CPUs); --jobs 1 is the plain one-checkout run) the checks are shared by N workers. Each worker
has its own git worktree of HEAD under %TEMP%\amut\wK, so this checkout is never modified; the worktrees are removed on
exit, and leftovers of a killed run are removed at the next start. Only committed code (HEAD) is mutated and tested.
Files keep their own line endings (CRLF or LF) so a restored file is byte for byte what it was.
"""
import argparse
import atexit
import queue
import shutil
import signal
import tempfile
import threading
import hashlib
import json
import os
import pathlib
import re
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
MUTANTS = ROOT / "scripts" / "mutants.json"
BACKUPS = ROOT / "build" / "mutation-backup"
DEFAULT_JAVA = r"C:\Program Files\Android\Android Studio\jbr"


AMUT = pathlib.Path(tempfile.gettempdir()) / "amut"


def restore_leftovers():
    """A previous run that was killed leaves backups behind; put those files back first."""
    if not BACKUPS.is_dir():
        return
    for marker in BACKUPS.glob("*.json"):
        info = json.loads(marker.read_text(encoding="utf-8"))
        target = ROOT / info["file"]
        original = pathlib.Path(info["backup"])
        if original.is_file():
            target.write_bytes(original.read_bytes())
            print(f"restored {info['file']} from an interrupted run")
        marker.unlink()
        original.unlink(missing_ok=True)


def apply(rel_file, find, replace, root=ROOT):
    """Returns the original bytes. Raises if the text to break is not there exactly once."""
    path = root / rel_file
    backups = root / "build" / "mutation-backup"
    original = path.read_bytes()
    text = original.decode("utf-8")
    crlf = "\r\n" in text
    f, r = (find.replace("\n", "\r\n"), replace.replace("\n", "\r\n")) if crlf else (find, replace)
    count = text.count(f)
    if count != 1:
        raise ValueError(f"{rel_file}: expected the text to appear once, found {count}: {find[:70]!r}")
    backups.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha1(rel_file.encode()).hexdigest()[:10]
    backup = backups / f"{key}.orig"
    backup.write_bytes(original)
    (backups / f"{key}.json").write_text(json.dumps({"file": rel_file, "backup": str(backup)}), encoding="utf-8")
    path.write_bytes(text.replace(f, r, 1).encode("utf-8"))
    return original, key


def put_back(rel_file, original, key, root=ROOT):
    backups = root / "build" / "mutation-backup"
    (root / rel_file).write_bytes(original)
    (backups / f"{key}.orig").unlink(missing_ok=True)
    (backups / f"{key}.json").unlink(missing_ok=True)


def gradle(tasks, root=ROOT):
    env = dict(os.environ)
    env.setdefault("JAVA_HOME", DEFAULT_JAVA)
    started = time.time()
    proc = subprocess.run([str(root / "gradlew.bat"), *tasks, "--console=plain", "--continue"], cwd=root, env=env, capture_output=True, text=True, encoding="utf-8", errors="replace")
    out = proc.stdout + proc.stderr
    failed = sorted(set(re.findall(r"^(\S+ > .+?) FAILED$", out, re.M)))
    compile_error = bool(re.search(r"^e: ", out, re.M)) or "Compilation error" in out
    return {"built": "BUILD SUCCESSFUL" in out, "failed": failed, "compile_error": compile_error, "seconds": time.time() - started}


def check(name, rel_file, find, replace, tasks, root=ROOT):
    original, key = apply(rel_file, find, replace, root)
    try:
        result = gradle(tasks, root)
    finally:
        put_back(rel_file, original, key, root)
    if result["compile_error"] and not result["failed"]:
        verdict, detail = "INVALID", "the broken code did not compile"
    elif result["failed"]:
        verdict = "CAUGHT"
        shown = ", ".join(t.split(" > ")[-1][:60] for t in result["failed"][:2])
        detail = f"{len(result['failed'])} failing: {shown}" + (" ..." if len(result["failed"]) > 2 else "")
    elif result["built"]:
        verdict, detail = "SURVIVED", "all tests passed with the bug in place"
    else:
        verdict, detail = "ERROR", "the build failed without a failing test"
    print(f"{verdict:<9} {name:<34} {result['seconds']:>5.0f}s  {detail}", flush=True)
    return verdict


def git(*args, cwd=ROOT):
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, encoding="utf-8", errors="replace")


def remove_worktrees():
    """Remove every worktree under AMUT (this run's or a killed run's), then prune."""
    listing = git("worktree", "list", "--porcelain").stdout
    for line in listing.splitlines():
        if line.startswith("worktree "):
            path = pathlib.Path(line[len("worktree "):])
            try:
                inside = AMUT.resolve() in path.resolve().parents
            except OSError:
                inside = False
            if inside:
                git("worktree", "remove", "--force", str(path))
                shutil.rmtree(path, ignore_errors=True)
    git("worktree", "prune")
    shutil.rmtree(AMUT, ignore_errors=True)


def cost(m):
    """Rough weight so slow checks start first: :app is the slowest module, then :alerts/:store/:platform."""
    tasks = " ".join(m["tasks"])
    return 100 if ":app" in tasks else 30 if re.search(r":(alerts|store|platform)", tasks) else 10


def run_parallel(chosen, jobs):
    remove_worktrees()
    atexit.register(remove_worktrees)
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: sys.exit(130))
    AMUT.mkdir(parents=True, exist_ok=True)
    jobs = max(1, min(jobs, len(chosen)))
    roots = []
    for k in range(jobs):
        root = AMUT / f"w{k}"
        made = git("worktree", "add", "--detach", str(root), "HEAD")
        if made.returncode != 0:
            print("could not create a worktree:", made.stderr.strip())
            return None
        roots.append(root)
        local = ROOT / "local.properties"
        if local.is_file():
            shutil.copy(local, root / "local.properties")
    work = queue.Queue()
    for m in sorted(chosen, key=cost, reverse=True):
        work.put(m)
    results = {}
    lock = threading.Lock()

    def worker(root):
        while True:
            try:
                m = work.get_nowait()
            except queue.Empty:
                return
            try:
                verdict = check(m["name"], m["file"], m["find"], m["replace"], m["tasks"], root)
            except ValueError as error:
                with lock:
                    print(f"INVALID   {m['name']:<34} {error}", flush=True)
                verdict = "INVALID"
            except Exception as error:  # a worker must never lose its other checks
                with lock:
                    print(f"ERROR     {m['name']:<34} {error}", flush=True)
                verdict = "ERROR"
            results[m["name"]] = verdict

    threads = [threading.Thread(target=worker, args=(r,), daemon=True) for r in roots]
    for t in threads:
        t.start()
    while any(t.is_alive() for t in threads):  # joined in slices so Ctrl-C is seen
        for t in threads:
            t.join(0.5)
    return [results.get(m["name"], "ERROR") for m in chosen]


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("names", nargs="*", help="names from scripts/mutants.json (default: all)")
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--file")
    parser.add_argument("--find")
    parser.add_argument("--replace")
    parser.add_argument("--task", action="append")
    parser.add_argument("--jobs", type=int, default=min(4, os.cpu_count() or 1), help="parallel workers on separate git worktrees (default min(4, CPUs); 1 = this checkout)")
    args = parser.parse_args()

    restore_leftovers()
    mutants = json.loads(MUTANTS.read_text(encoding="utf-8"))
    if args.list:
        for m in mutants:
            print(f"{m['name']:<34} {' '.join(m['tasks']):<34} {m['why']}")
        return 0
    if args.file:
        chosen = [{"name": "ad-hoc", "file": args.file, "find": args.find, "replace": args.replace, "tasks": args.task or [":core:test"]}]
    else:
        chosen = [m for m in mutants if not args.names or m["name"] in args.names]
        unknown = set(args.names) - {m["name"] for m in mutants}
        if unknown:
            print("unknown names:", ", ".join(sorted(unknown)))
            return 2
    verdicts = []
    if args.jobs > 1 and len(chosen) > 1 and not args.file:
        verdicts = run_parallel(chosen, args.jobs)
        if verdicts is None:
            return 2
        chosen = []
    for m in chosen:
        try:
            verdicts.append(check(m["name"], m["file"], m["find"], m["replace"], m["tasks"]))
        except ValueError as error:
            print(f"INVALID   {m['name']:<34} {error}")
            verdicts.append("INVALID")
    caught = verdicts.count("CAUGHT")
    print(f"\n{caught} of {len(verdicts)} caught" + ("" if caught == len(verdicts) else "; the others need attention"))
    return 0 if caught == len(verdicts) else 1


if __name__ == "__main__":
    sys.exit(main())
