# Contributing

Thanks for helping out!

- **Bugs:** report them at https://mattsmods.vercel.app/qualityclip/report-bug (GitHub issues redirect there)
- **Build:** JDK 25 + Gradle 9.x, `gradle build -Pmc_target=26.1.2` (or 26.2 / 26.3)
- **Version-specific code** lives in `versions/<target>/`; everything else is shared in `src/main`.
- Please test on every supported Minecraft version before opening a PR.
