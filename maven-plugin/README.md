# Kensa Maven Plugin

Maven plugin for the Kensa BDD testing framework — assembles multiple test executions into a single browsable site report (site mode, v0.8+).

## Documentation

- **[Quickstart](https://kensa.dev/docs/intro)**
- **[Maven plugin reference](https://kensa.dev/docs/build-plugins/maven-plugin)**
- **[Site mode](https://kensa.dev/docs/build-plugins/site-mode)** — multi-execution aggregated reports
- **[Configuration reference](https://kensa.dev/docs/api/configuration)**

## Coordinates

```xml
<plugin>
  <groupId>dev.kensa</groupId>
  <artifactId>kensa-maven-plugin</artifactId>
  <version>VERSION</version>
</plugin>
```

See [Maven Central](https://central.sonatype.com/artifact/dev.kensa/kensa-maven-plugin) for the latest version.

## kensa-core compatibility

The plugin and `dev.kensa:kensa-core` version independently (since plugin v0.9.0). Each plugin release ships a default kensa-core it was tested against; override `<kensaCoreVersion>` to pin a different one within the supported range.

| Plugin     | Default kensa-core | Min kensa-core | Notes                                |
| ---------- | ------------------ | -------------- | ------------------------------------ |
| 0.9.10     | 0.8.14             | 0.8.0          | Default kensa-core bumped to 0.8.14 (path-expression rendering fixes #168–#172; protected phrase word boundaries #166; parameterised header hover #165; from 0.8.13: #162/#163); no Maven changes |
| 0.9.9      | 0.8.12             | 0.8.0          | Default kensa-core bumped to 0.8.12 (value class params in `@ExpandableSentence`/`@RenderedValue` #160; array parameter types #161; multi-assertion polling blocks from 0.8.11); no Maven changes |
| 0.9.8      | 0.8.10             | 0.8.0          | Default kensa-core bumped to 0.8.10 (`@Fixture` factory functions; `@RenderedValue` fixes #149/#150); no Maven changes |
| 0.9.7      | 0.8.8              | 0.8.0          | Version bump only — paired Gradle-plugin site-mode resolution fix; no Maven changes |
| 0.9.6      | 0.8.8              | 0.8.0          | Default kensa-core bumped to 0.8.8   |
| 0.9.5      | 0.8.7              | 0.8.0          | Default kensa-core bumped to 0.8.7 (the Kotlin 2.4.0 floor is a Gradle-apply-time change only) |
| 0.9.4      | 0.8.5              | 0.8.0          | Default kensa-core bumped to 0.8.5 (listener short-circuits when no Kensa tests ran); the `outputSourceSets` split is Gradle-only |
| 0.9.3      | 0.8.4              | 0.8.0          | Multi-submodule site aggregation; default kensa-core bumped to 0.8.4 |
| 0.9.2      | 0.8.3              | 0.8.0          | Default kensa-core bumped to 0.8.3   |
| 0.9.1      | 0.8.1              | 0.8.0          | Site-mode: `<sourceTitles>` mojo parameter, per-source component diagrams |
| 0.9.0      | 0.8.0              | 0.8.0          | First decoupled release              |
| 0.7.x      | 0.7.x              | —              | Same-version pairing (no override)   |

> v0.8.0 was withdrawn — its POM declared an unpublished `dev.kensa:site-common` dep. Use 0.9.0 or later.

Override the kensa-core version on the mojo configuration:

```xml
<configuration>
  <kensaCoreVersion>0.8.1</kensaCoreVersion>
</configuration>
```

No upper bound — newer kensa-cores are assumed compatible until proven otherwise. A version below the minimum fails fast at execution time.

## Site-mode source titles

Set per-source labels for the aggregated site sidebar via `<sourceTitles>` on the mojo configuration. Entries here override the title the test runtime wrote into each source's `configuration.json` (and rewrite that file so the standalone per-source HTML page title matches).

```xml
<configuration>
  <expectedSourceIds>
    <expectedSourceId>uiTest</expectedSourceId>
    <expectedSourceId>scenarioTest</expectedSourceId>
  </expectedSourceIds>
  <sourceTitles>
    <uiTest>UI Tests</uiTest>
    <scenarioTest>Scenario Tests</scenarioTest>
  </sourceTitles>
</configuration>
```

Sources without an entry keep whatever `Kensa.konfigure { titleText = "..." }` set in code.
