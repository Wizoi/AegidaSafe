# Third-party data and software

## Scam and spying-app lists (the signed feed)

The app checks links and installed apps against a signed list that is built daily in this repository's workflow and
published at https://wizoi.github.io/Aegida-Feed/ (with the notices in `NOTICE.txt` there).

- **Phishing.Database**: https://github.com/Phishing-Database/Phishing.Database, MIT license, copyright Mitchell Krog,
  Nissar Chababy and Phishing.Database contributors. Used for the scam website and link entries. Excerpts of its lists
  are used as test data under `feedtool/src/test/resources/feeds/`.
- **Stalkerware Indicators of Compromise**: https://github.com/AssoEchap/stalkerware-indicators, published by Echap
  under Creative Commons Attribution 4.0 International (CC BY 4.0), https://creativecommons.org/licenses/by/4.0/.
  Only the package names and signing-certificate fingerprints are used, stored in the feed as 8-byte SHA-256
  prefixes (a change from the original). A copy of `ioc.yaml` is kept as test data
  (`feedtool/src/test/resources/feeds/echap-ioc.yaml`). No endorsement by Echap or the Coalition Against Stalkerware
  is suggested.

## Software

The app uses open-source libraries (Kotlin, AndroidX, Jetpack Compose, Room, Hilt, WorkManager, OkHttp and others)
under their own licenses, as listed in `gradle/libs.versions.toml`. The Gradle wrapper is Apache-2.0.

Aegida itself is released under the MIT license, in `LICENSE`.
