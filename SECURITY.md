# Reporting a security problem

Aegida runs on an older person's phone and can tell her family when something is wrong, so a flaw in it matters.

Please report a problem privately, not in a public issue: use GitHub's "Report a vulnerability" (Security tab of this
repository). If that is not available, open an issue that says only "security report, please contact me" and leave out
the details.

What helps: what you did, what you expected, what happened, the Android version and whether it needs a second app on the
phone. I will say thanks, fix it, and credit you if you want.

Things that are deliberately public and not secrets: the feed signing **public** key (`feedtool/feed-public-key.txt`), the
feed itself, and everything in this repository. The feed signing **private** key and the deploy key exist only as GitHub
Actions secrets.
