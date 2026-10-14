aegida-v1-emulator.db: a real Room schema-version-1 database. Made 2026-10-14 by the debug APK built (-PlocalBuild) from commit e2f07ce
(the parent of ec9cde4, the last commit before schema 2), installed on the Aegida36 emulator (emulator-5554), notification access
granted, setup.stepsDone/pictureId/ownerName/alertsOn=false seeded the way scripts/emulator-checks.sh does, then left to run
(app starts, a daily check, two test notifications with links). Pulled with run-as, checkpointed to one file, journal_mode=delete,
user_version 1. Rows: setting 17, check_run 6, finding 1, history 1, outbox 0 (no guardian was added, so nothing was queued). No personal data.
