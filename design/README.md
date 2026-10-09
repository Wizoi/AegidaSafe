# Design artifacts

- `storyboard/project/` — the published storyboard boards (`*.dc.html`, `canvas.json`). Published as a
  Claude design artifact: https://claude.ai/artifact/X8MFEDC2skyGQdt9gePmbS (version 17, Oct 8, 2026).
- `storyboard/generators/gen_v2.py` — the current generator (Oct 8, 2026): draws the app as built and reads every
  word from `Texts.kt` and `Problem.kt`. Run `python design/storyboard/generators/gen_v2.py`. See
  `docs/storyboard-status.md`. The older scripts below drew the first, planned storyboard.
- `storyboard/generators/` — Python scripts that generated the boards (`gen.py` for scenarios,
  `gen_main.py` for the overview, `gen_logo.py` + `logos.py` for the logo board). They were written to run
  from a scratch folder and have hard-coded paths near the top and the Setup picture grid is copied from
  `Setup.backup.html`; edit paths before reusing.
- `logo/` — the locked logo (idea 1, sunflower shield): `aegida-logo.svg`, `aegida-app-icon.svg`,
  `aegida-notification-icon.svg`, plus the six-idea board.
