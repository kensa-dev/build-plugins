# <img src="./Logo.svg" alt="Kensa Logo" style="width: 40px; vertical-align: middle;"/> Kensa Gradle Plugin

![Latest Release](https://img.shields.io/github/v/release/kensa-dev/kensa)

A Gradle plugin for the acceptance test framework Kensa. To use the full functionality of Kensa's `@NestedSentence` & `@RenderedValue` annotations (collection and rendering of function arguments), you must apply this plugin to your Kotlin project.

## What it does
- Applies the Kensa Kotlin compiler plugin `dev.kensa.compiler-plugin` to the source sets listed in `sourceSets`
- Adds `dev.kensa:kensa-core:<version>` (capability `dev.kensa:core-hooks`) to the runtime classpath of `sourceSets ∪ outputSourceSets`
- Exposes a `kensa` extension:
    - `enabled`: master switch (default: `true`).
    - `debug`: extra diagnostics from the compiler plugin (default: `false`).
    - `sourceSets`: Kotlin compilation names the compiler plugin instruments (default: `["test"]`). Set to `emptySet()` if your project doesn't use the `@RenderedValue` / `@ExpandableSentence` argument-capture features.
    - `outputSourceSets`: source sets whose Test tasks emit Kensa output — `build/kensa/index.html`, JSON indices, the `Kensa Output : …` banner, and (in site mode) per-source bundles (default: `["test"]`). Independent of `sourceSets`.

`sourceSets` and `outputSourceSets` are independent because the two concerns are independent — a project can have expandable-sentence support code in `main` (needing the compiler plugin there) while only its `test` source set produces reports.

## Quick start
1. Add the plugin to your build.gradle.kts:
2. Configure the plugin as needed.
3. Build as normal.
``` kotlin
   plugins {
       id("dev.kensa.gradle-plugin") version "<plugin-version>"
   }
```

Configure (optional)

``` kotlin
kensa {
    enabled.set(true)                                       // default true
    debug.set(false)                                        // default false
    sourceSets.set(setOf("test"))                           // default "test"
    outputSourceSets.set(setOf("test", "acceptanceTest"))   // default "test"
}
```

Common patterns:

```kotlin
// Pattern A — no compiler-plugin features used; just `KensaTest` and the runtime.
kensa {
    sourceSets.set(emptySet())
    outputSourceSets.set(setOf("test"))
}

// Pattern B — `@ExpandableSentence` support code in main; tests only consume it.
kensa {
    sourceSets.set(setOf("main"))
    outputSourceSets.set(setOf("test"))
}

// Pattern C — expandable code across multiple source sets, multiple output tasks.
kensa {
    sourceSets.set(setOf("main", "test", "acceptanceTest"))
    outputSourceSets.set(setOf("test", "acceptanceTest"))
}
```

Build as normal; the plugin attaches to the selected compilations.

## Documentation

User documentation lives at **[kensa.dev](https://kensa.dev/docs/intro)**.

Plugin-specific guides:
- **[Gradle plugin](https://kensa.dev/docs/build-plugins/gradle-plugin)**
- **[Maven plugin](https://kensa.dev/docs/build-plugins/maven-plugin)**
- **[Site mode](https://kensa.dev/docs/build-plugins/site-mode)** — multi-source aggregated reports, including [CI / hosted use](https://kensa.dev/docs/build-plugins/site-mode#ci--hosted-use)
