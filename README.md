# Bloom Android Foundation Plugin

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

Before the first release, create the `com.vinted.bloom` plugin entry in JetBrains Marketplace. The release pipeline
expects these Jenkins Secret Text credentials:

- `jetbrains-marketplace-publish-token` → `PUBLISH_TOKEN`
- `jetbrains-marketplace-certificate-chain` → `CERTIFICATE_CHAIN`
- `jetbrains-marketplace-private-key` → `PRIVATE_KEY`
- `jetbrains-marketplace-private-key-password` → `PRIVATE_KEY_PASSWORD`

The certificate chain and private key may be stored as the base64-encoded values accepted by the IntelliJ Platform
Gradle Plugin. They must never be committed to the repository.

Create a plugin release by updating the version, merging the change, and creating a tag with the matching version:

```text
bloom-plugin-v0.1.21
```

The plugin Jenkins pipeline runs tests and builds the ZIP for every change. For a `bloom-plugin-v*` tag it also checks
that the tag matches the Gradle project version, signs the plugin, and runs `publishPlugin`.

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
