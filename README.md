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

Create the `com.vinted.bloom` listing in JetBrains Marketplace and add `JETBRAINS_MARKETPLACE_TOKEN` as a GitHub
repository secret. The GitHub Actions workflow builds and tests pull requests and pushes to `master`. It publishes
when a `bloom-plugin-v*` tag is pushed, provided the tag matches the version in `build.gradle.kts`.

To release, update the version, merge the change, then push a matching tag. For version `0.1.21`, use:

```text
bloom-plugin-v0.1.21
```

The first Marketplace upload must be made manually after creating the listing. To build the plugin locally without
publishing:

```text
./gradlew test buildPlugin
```

The generated ZIP is written to `build/distributions/`.

## Design direction

The gutter marker is the primary affordance because it stays beside the line number and can render a real color or
drawable preview. Hovering shows the resolved value and source file. A future settings/tool-window surface can use
Jewel/IntelliJ UI components for filtering, theme selection, and a full token-resolution chain without coupling the
editor marker to Compose Desktop.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
