# Contributing to KMapper

Thanks for taking the time to contribute. Please read this guide before opening a PR.

## Table of Contents

- [Module Layout](#module-layout)
- [Build & Test](#build--test)
- [Code Style](#code-style)
- [Commit Messages](#commit-messages)
- [Pull Request Process](#pull-request-process)

---

## Module Layout

```
core/                    # Annotations, MappingException, MapTypeConverter registry, built-in converters, MappableEnum
processor/               # KSP code generator (@MapTo/@MapFrom → toX() extensions); tested with kctfork
converters-immutable/    # kotlinx-collections-immutable wrappers (PersistentList, ImmutableList, …)
converters-arrow/        # Arrow NonEmptyList/NonEmptySet wrappers
converters-datetime/     # String/Long ↔ LocalDate/LocalDateTime/Instant/… (kotlinx on KMP, java.time on JVM/Android)
converters-bignumber/    # String/Double/Long/Int ↔ BigDecimal/BigInteger (ionspin on KMP, java.math on JVM/Android)
converters-uuid/         # String ↔ kotlin.uuid.Uuid; String/Uuid ↔ java.util.UUID (JVM/Android)
converters-okio/         # String/ByteArray ↔ okio.ByteString; String ↔ okio.Path
converters-uri/          # String ↔ java.net.URI (JVM), android.net.Uri (Android), NSURL (iOS)
validators/              # EmailValidator, UrlValidator for @ValidateFrom/@ValidateTo
integration-test/        # End-to-end tests that run the full KSP pipeline
sample/                  # Sample project showing typical usage
```

All modules share a single `gradle/libs.versions.toml` version catalog.

---

## Build & Test

### Prerequisites

- JDK 17+
- Android SDK (set `ANDROID_HOME` or `local.properties`)
- Gradle wrapper included — no separate install needed

### Build everything

```bash
./gradlew build
```

### Run tests

| Target | Command |
|--------|---------|
| All JVM tests (including processor via kctfork) | `./gradlew test` |
| Specific module JVM tests | `./gradlew :core:jvmTest` |
| iOS Simulator tests (KMP modules) | `./gradlew :core:iosSimulatorArm64Test` |
| Integration tests | `./gradlew :integration-test:test` |

The `processor` module is pure JVM and its tests use **kctfork** (`dev.zacsweers.kctfork`) to spin up an in-process KSP compilation, so no special setup is required — `./gradlew :processor:test` is sufficient.

### Useful shortcuts

```bash
# Compile-check a single module without tests
./gradlew :core:compileKotlinJvm

# Check all modules compile
./gradlew compileKotlin
```

### Public API compatibility

The public API of every published module is guarded by the **binary-compatibility-validator** plugin — JVM dumps in `api/*.api` and native/KLIB dumps in `api/*.klib.api` are checked into the repo, and CI runs `./gradlew apiCheck`.

If you **intentionally** change public API, regenerate and commit the baselines:

```bash
./gradlew apiDump
```

A failing `apiCheck` means your change altered the public API — revert it if unintended, or run `apiDump` if the change is deliberate.

---

## Code Style

Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html). The project does not currently enforce a linter automatically, so please run your IDE's Kotlin formatter before pushing.

Key points:
- 4-space indentation (no tabs)
- Max line length: 120 characters
- Prefer `val` over `var`; prefer expression functions for single-expression bodies
- All public API must have KDoc

---

## Commit Messages

This project uses **[Conventional Commits](https://www.conventionalcommits.org/)**. Match the style of existing history:

```
<type>(<scope>): <short description>
```

Common types: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `perf`.  
Scope is the module name (e.g. `processor`, `core`, `converters-datetime`) or omitted for repo-wide changes.

Examples:

```
feat(processor): support @MapFrom for reverse mapping
fix(core): throw MappingError.RequiredFieldMissing for nullable→non-null fields
docs(readme): add installation instructions for converters-arrow
test(integration-test): add E2E test for Map<K,V> mapping
chore(release): 1.1.0
```

Breaking changes: add `!` after the type/scope and a `BREAKING CHANGE:` footer.

---

## Pull Request Process

1. **Fork** the repo and create a branch from `main`:
   ```bash
   git checkout -b feat/my-feature
   ```

2. **Make your changes** in the appropriate module(s).

3. **Add or update tests** to cover your change. All existing tests must continue to pass:
   ```bash
   ./gradlew test
   ```

4. **Update documentation** if you change public API, add a new converter/validator, or change behaviour. Documentation source lives in `docs/guide-en/` (English) and `docs/guide/` (Turkish).

5. **Open a PR** against `main`. Fill in the PR template completely.

6. **One approving review** from a maintainer is required before merge.

A maintainer will merge once CI is green and review is approved. Thank you for contributing!
