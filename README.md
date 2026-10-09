# Aegida

Aegida (from "aegis", a shield) is an Android app that helps an older parent keep her phone set up
safely. It hides fake virus pop-ups, spots dangerous apps and links, notices when a safety setting
changes, and tells family "guardians" when something serious happens.

Status: working app, being tested on an emulator before a Play closed test. Link and website-pop-up checks, drift and new-app
detection, remote-control and spying-app detection, and guardian alerts are built; see the [build plan](docs/build-plan.md)
for what is done and what is not. Research is in [research/](research/), storyboard and logo in [design/](design/README.md).

The source is public so that anyone can audit what the app does on an older person's phone. What it reads, what it
stores and what it sends is described in [docs/privacy-and-data.md](docs/privacy-and-data.md). To report a security
problem, see [SECURITY.md](SECURITY.md).

- What it does: [docs/product-brief.md](docs/product-brief.md)
- How it is built: [docs/aegida-technical-design.md](docs/aegida-technical-design.md)
- Why decisions were made: [docs/decisions.md](docs/decisions.md)
- Build plan and status: [docs/build-plan.md](docs/build-plan.md)
- Working rules: [CLAUDE.md](CLAUDE.md)
- Findings log: [docs/findings.md](docs/findings.md)
- Emulator versus device gaps: [docs/emulator-vs-device.md](docs/emulator-vs-device.md)
- Samsung validation options: [docs/samsung-validation-options.md](docs/samsung-validation-options.md)

Distribution: Google Play closed testing first. Third-party data and licences: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
