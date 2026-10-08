"""Mutation checks: break one thing on purpose, run the tests, and require a test to go red.

    python scripts/mutation-check.py --list                  # the standing checks in scripts/mutants.json
    python scripts/mutation-check.py                         # run all of them
    python scripts/mutation-check.py feed-signature          # run named ones
    python scripts/mutation-check.py --file F --find A --replace B --task :core:test   # one ad-hoc check

Each result is one of:
  CAUGHT    a test failed with the bug in place (good)
  SURVIVED  every test still passed (the tests do not guard that behaviour; fix the tests)
  INVALID   the broken code did not compile, so it proved nothing (fix the mutant)
  ERROR     the build failed in some other way

The file is always put back, even on Ctrl-C or a crash; a backup is kept in build/mutation-backup/ until the run ends
and is restored on the next start if a run was killed. Exit code 0 only if every check was CAUGHT.
Files keep their own line endings (CRLF or LF) so a restored file is byte for byte what it was.
"""
import argparse
import hashlib
import json
import os
import pathlib
import re
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
GRADLEW = ROOT / "gradlew.bat"
MUTANTS = ROOT / "scripts" / "mutants.json"
BACKUPS = ROOT / "build" / "mutation-backup"
DEFAULT_JAVA = r"C:\Program Files\Android\Android Studio\jbr"


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


def apply(rel_file, find, replace):
    """Returns the original bytes. Raises if the text to break is not there exactly once."""
    path = ROOT / rel_file
    original = path.read_bytes()
    text = original.decode("utf-8")
    crlf = "\r\n" in text
    f, r = (find.replace("\n", "\r\n"), replace.replace("\n", "\r\n")) if crlf else (find, replace)
    count = text.count(f)
    if count != 1:
        raise ValueError(f"{rel_file}: expected the text to appear once, found {count}: {find[:70]!r}")
    BACKUPS.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha1(rel_file.encode()).hexdigest()[:10]
    backup = BACKUPS / f"{key}.orig"
    backup.write_bytes(original)
    (BACKUPS / f"{key}.json").write_text(json.dumps({"file": rel_file, "backup": str(backup)}), encoding="utf-8")
    path.write_bytes(text.replace(f, r, 1).encode("utf-8"))
    return original, key


def put_back(rel_file, original, key):
    (ROOT / rel_file).write_bytes(original)
    (BACKUPS / f"{key}.orig").unlink(missing_ok=True)
    (BACKUPS / f"{key}.json").unlink(missing_ok=True)


def gradle(tasks):
    env = dict(os.environ)
    env.setdefault("JAVA_HOME", DEFAULT_JAVA)
    started = time.time()
    proc = subprocess.run([str(GRADLEW), *tasks, "--console=plain", "--continue"], cwd=ROOT, env=env, capture_output=True, text=True, encoding="utf-8", errors="replace")
    out = proc.stdout + proc.stderr
    failed = sorted(set(re.findall(r"^(\S+ > .+?) FAILED$", out, re.M)))
    compile_error = bool(re.search(r"^e: ", out, re.M)) or "Compilation error" in out
    return {"built": "BUILD SUCCESSFUL" in out, "failed": failed, "compile_error": compile_error, "seconds": time.time() - started}


def check(name, rel_file, find, replace, tasks):
    original, key = apply(rel_file, find, replace)
    try:
        result = gradle(tasks)
    finally:
        put_back(rel_file, original, key)
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


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("names", nargs="*", help="names from scripts/mutants.json (default: all)")
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--file")
    parser.add_argument("--find")
    parser.add_argument("--replace")
    parser.add_argument("--task", action="append")
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
