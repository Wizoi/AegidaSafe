# Windows notes

Traps that cost time while building Aegida on Windows 11. Each one was hit for real; the dates are when.

## Which shell for what
- **Git Bash (the Bash tool):** git, Gradle, `scripts/*.sh`, adb. This is the default.
- **PowerShell (5.1):** anything Windows-only: `winget`, environment variables, Defender, the registry.
- **Neither is good for pasting code with backslashes.** Write it to a file with the editor tools (Write/Edit) and run
  the file. If it must be inline, use a PowerShell here-string (`@'...'@ | python -I -`), which arrives exactly as written.

## Backslashes in the Bash tool (Oct 8)
The Bash tool turns `\\` into `\` before bash sees the command, even inside a quoted heredoc (`<<'EOF'`), where real
bash would leave it alone. A Python heredoc containing `"\\b"` therefore gave Python `"\b"`, a backspace character, and
`"\\n"` became a real newline. This produced silently wrong regexes and source files (findings #34, the `RoomStores.kt`
syntax error). Test: `python -I - <<'EOF'` with `print(repr("a\\bc"))` prints `'a\x08c'` in Bash and `'a\\bc'` in PowerShell.
- Never put backslashes in inline Bash code. Use Write/Edit, or a PowerShell here-string.
- A control character in a source file is invisible in a diff. Scan for them:
  `grep -rlP '[\x00-\x08\x0b\x0c\x0e-\x1f]' --include=*.kt --include=*.kts --include=*.xml --include=*.py --include=*.sh .`

## Line endings
- Python's `read_text`/`write_text` on Windows read `\r\n` as `\n` and **write `\r\n`**. Edit scripts with
  `read_bytes().decode()` and `write_bytes()` so a file keeps its endings. `.gitattributes` keeps `*.sh` as LF.
- `git config core.autocrlf input` stops the "LF will be replaced by CRLF" warning on every `git add`.

## adb
- `adb shell <args>` joins the arguments and the **phone's shell splits them again**. Anything with spaces must go over
  as one string: `adb shell "cmd notification post -S bigtext -t 'Title' tag 'text with spaces'"` (findings #33).
- Git Bash rewrites arguments that look like paths (`/sdcard/x` becomes `C:/Program Files/Git/sdcard/x`). Use
  `MSYS_NO_PATHCONV=1` for device paths; `scripts/emulator-checks.sh` already does.
- `am start` on an app already in front does not refresh it; leave and return, as Mom would from Settings.
- A device restarts the periodic WorkManager job under a new id each run; WorkManager will not run it early even when
  forced, so advance the clock first (`settings put global auto_time 0`, then `cmd alarm set-time <ms>`).

## Gradle
- `JAVA_HOME` is set permanently to Android Studio's JBR (user setting); `adb` and `emulator` are on PATH.
- Success is the words `BUILD SUCCESSFUL`, not an exit code through a pipe (CLAUDE.md).
- `gradlew clean` can fail on Windows because the daemon holds a file open. Run `./gradlew --stop` first.
- `./gradlew build` runs the unit tests for both the debug and release variants, so a Compose UI test that passes in
  `testDebugUnitTest` can fail in release (findings #35). The full build is the gate, not one variant.
- If lint contradicts a file you can see, rerun it with `--rerun` on `lintAnalyzeDebug`, `lintReportDebug` and
  `lintDebug` before debugging (findings #31).

## PowerShell 5.1
- No `&&` or `||`; chain with `;` and test `$?`.
- A bare empty string argument to a program is dropped: `ssh-keygen -N ""` fails, `ssh-keygen -N '""'` works.
- `Set-Content`/`Out-File` default to the system code page; pass `-Encoding utf8`.
