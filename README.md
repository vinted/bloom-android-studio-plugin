# Bloom Android Studio Plugin

An Android Studio plugin that shows Android Bloom foundation values in the editor gutter, next to the line numbers.

The first slice supports:

- `BloomTheme.colors.*` with a color swatch and resolved raw color.
- `BloomTheme.dimensions.*`, `mediaSizes.*`, `shadows.*`, `borderRadii.*`, `borderWidths.*`, `spacerSizes.*`, and
  `opacities.*` with a metric marker and resolved value.
- `BloomTheme.alignments.*`, `borderThemes.*`, `textHighlights.*`, and `typographies.*` with the generated foundation
  documentation as the resolved value.
- `R.color.*`, `R.dimen.*`, `R.drawable.*`, and XML `@color/...`, `@dimen/...`, `@drawable/...` references.
- Simple Kotlin/Java aliases and Android resource aliases, resolved until the final value.
- Raster drawable previews when the drawable is present in the current project. Vector/XML drawables receive a
  drawable marker and source path; vector-path rendering is intentionally the next iteration.

## Run in Android Studio

From the repository root:

```text
./gradlew runIde
```

The default target is Android Studio `2024.1.2.12`, which matches the Java 17 toolchain used by this repository. To run
against an installed local IDE instead, pass its application directory:

```text
./gradlew runIde -PandroidStudioPath="/Applications/Android Studio.app/Contents"
```

The plugin is deliberately a standalone Gradle project. It is not included in the Android library build, because it
targets the IDE rather than Android and has a separate IntelliJ Platform dependency graph.

## Release cycle

The Marketplace plugin ID is `com.vinted.bloom`. Keep the plugin version in `build.gradle.kts` independent from the
Android Bloom library version.

Before the first release, create the `com.vinted.bloom` plugin entry in JetBrains Marketplace. Add the Marketplace
token as a repository secret named `JETBRAINS_MARKETPLACE_TOKEN`. Configure a GitHub Actions environment named
`jetbrains-marketplace` with these signing secrets:

- `CERTIFICATE_CHAIN`
- `PRIVATE_KEY`
- `PRIVATE_KEY_PASSWORD`

The certificate chain and private key may be stored as the base64-encoded values accepted by the IntelliJ Platform
Gradle Plugin. They must never be committed to the repository.

The GitHub Actions workflow runs tests and builds the plugin for pull requests and pushes to `master`. It publishes
only when a `bloom-plugin-v*` tag is pushed, and checks that the tag matches the version in `build.gradle.kts`.
Create a release by updating the version, merging the change, and creating a matching tag:

```text
bloom-plugin-v0.1.21
```

For example, version `0.1.21` is released with tag `bloom-plugin-v0.1.21`. The first Marketplace upload may need to be
done manually to create and configure the listing before automated uploads can publish updates.

To verify the release locally without publishing:

```text
./gradlew test buildPlugin
```

The generated ZIP is written to `build/distributions/`.

## Design direction

The gutter marker is the primary affordance because it stays beside the line number and can render a real color or
drawable preview. Hovering shows the resolved value and source file. A future settings/tool-window surface can use
Jewel/IntelliJ UI components for filtering, theme selection, and a full token-resolution chain without coupling the
editor marker to Compose Desktop.
