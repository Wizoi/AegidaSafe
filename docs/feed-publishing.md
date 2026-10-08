# Publishing the feed (one-time setup)

How the scam lists reach Mom's phone: `.github/workflows/build-feed.yml` (private AegidaSafe) builds and signs
`feed.bin`/`feed.sig` every day and pushes them to the `gh-pages` branch of the public
[Aegida-Feed](https://github.com/Wizoi/Aegida-Feed) repo, served at `https://wizoi.github.io/Aegida-Feed/`.
Decisions: `decisions.md` (Build step 1 choices, Feed publishing). Sources and licences: findings #27.

Only Phishing.Database (MIT) is published. URLhaus stays out until abuse.ch confirms in writing that republishing
is allowed.

## What you do once (keys never pass through chat)

1. **Signing key.** In PowerShell, from the AegidaSafe folder (Gradle and the tool both need Android Studio's
   Java, which is not on the PATH, so set it first; it lasts only for that window):
   ```
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat :feedtool:installDist
   .\feedtool\build\install\feedtool\bin\feedtool.bat keygen --out $HOME\aegida-keys
   ```
   - GitHub, repo **AegidaSafe**, Settings, Secrets and variables, Actions, new secret
     `AEGIDA_FEED_SIGNING_KEY` = the contents of `feed-private.txt`.
   - Copy the contents of `feed-public.txt` into `feedtool/feed-public-key.txt` in this repo and commit it
     (a public key is safe to commit). The app will embed the same key.
   - Keep `feed-private.txt` in your password manager, then delete it from disk. **If it is lost, the app
     has to be updated with a new public key; phones cannot be re-keyed remotely.**
2. **Deploy key** (lets the workflow write to Aegida-Feed and nothing else):
   ```
   ssh-keygen -t ed25519 -N '""' -C aegida-feed-deploy -f $HOME\aegida-keys\feed_deploy_key
   ```
   (Windows PowerShell 5.1 drops a bare `""`, hence the `'""'`. Or leave `-N` out and press Enter at both
   passphrase prompts: the workflow needs a key with no passphrase.)
   - Repo **Aegida-Feed**, Settings, Deploy keys, add the `.pub` file, tick **Allow write access**.
   - Repo **AegidaSafe**, secret `AEGIDA_FEED_DEPLOY_KEY` = the contents of the private file
     (`feed_deploy_key`, including the BEGIN/END lines). Delete the local copies afterwards.
3. **Aegida-Feed content.** Push the `main` branch of the local `Aegida-Feed` folder (README and `site/`).
4. **Pages.** Aegida-Feed, Settings, Pages, deploy from branch `gh-pages`, folder `/ (root)`. The branch exists
   after the first workflow run, so run step 5 first, then set this.
5. **First run.** AegidaSafe, Actions, "Build feed", Run workflow. Check that
   `https://wizoi.github.io/Aegida-Feed/feed.bin` downloads (about 9 MB).

## Checks the workflow makes before publishing
- The signature must match `feedtool/feed-public-key.txt`.
- The same sanity rules the app applies: at least 1,000 entries, not dated in the future, newer than the live
  feed, not less than half its size. A failed check stops the run; the previous feed stays live.

## Notes
- The branch is replaced with a single new commit each day, so the repo does not grow by 9 MB a day.
- GitHub disables scheduled workflows after 60 days with no activity in a repo; AegidaSafe is active, and a
  manual run re-enables it.
- The phone must keep working if the feed is stale or missing: `HealthDetector` reports "lists old" after
  3 days and nothing is ever called "safe" because of a feed.
