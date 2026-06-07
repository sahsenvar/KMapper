# OSS Maturity (Section B) — Implementation Plan

**Date:** 2026-06-07 · **Repo:** KMapper (`io.github.sahsenvar:kmapper-*`) · **Version:** `1.0.0` (DO NOT bump)

**Goal:** bring KMapper to OSS-1.0 maturity — CI, ABI guard, changelog, badges, API docs, community files.

**Stack:** KMP (jvm, android [AGP 9.1.0], iosArm64, iosSimulatorArm64), Kotlin 2.3.10, KSP 2.3.6, vanniktech maven-publish 0.33.0, kctfork tests (jvmTarget 21). Gradle builds run via the context-mode MCP tool.

## Concurrency & conflict strategy
5 subagents run **concurrently in the same working tree** with **disjoint file ownership**. They do **NOT** `git add/commit/push` (avoids `.git/index.lock` races) — the **orchestrator commits once at the end** and pushes (active gh account is `ssenvar` → must `gh auth switch --user sahsenvar` first, then restore). Only the Build-infra agent runs Gradle.

| # | Agent | Owns (sole writer) | Gradle? |
|---|-------|--------------------|---------|
| A | CI | `.github/workflows/ci.yml`, `.github/workflows/publish.yml` | no |
| B | Build-infra (B2 binary-compat + B5 Dokka) | `gradle/libs.versions.toml`, `build.gradle.kts` (root + every module), `**/api/*.api` | yes |
| C | CHANGELOG | `CHANGELOG.md` | no |
| D | Badges | `README.md` | no |
| E | Community | `.github/CONTRIBUTING.md`, `.github/ISSUE_TEMPLATE/*`, `.github/PULL_REQUEST_TEMPLATE.md`, `.github/dependabot.yml` | no |

A and E both create files under `.github/` but never the same file.

## Items
- **A (B1) CI** — `ci.yml`: on push/PR to `main`, runner `macos-latest` (arm64 → can run `iosSimulatorArm64Test`), Temurin JDK 21, Gradle cache, `./gradlew build`. `publish.yml`: on tag `v*`, `./gradlew publishToMavenCentral` reading vanniktech secrets via `ORG_GRADLE_PROJECT_mavenCentralUsername/Password` + `ORG_GRADLE_PROJECT_signingInMemoryKey(+KeyId/Password)` from GitHub Actions secrets. The user must add the repo secrets — note this in the agent output.
- **B (B2) binary-compatibility-validator** — add the plugin (catalog + apply at root), run `./gradlew apiDump` → commit `*.api` baselines (locks the 1.0 public API). **(B5) Dokka 2.x** — apply to publishable modules; vanniktech maven-publish auto-wires the javadoc jar from Dokka. Verify `apiCheck` + a Dokka generate task succeed via context-mode. Versions are sensitive against Kotlin 2.3.10 — consult current docs (context7/web); if a plugin is incompatible, report the blocker instead of forcing.
- **C (B3) CHANGELOG.md** — Keep-a-Changelog format, `1.0.0` entry with the full feature set (pull from README + `docs/guide`): `@MapTo`/`@MapFrom`, type converters, `MappableEnum`, collection wrappers, null-safety, validation seam (`@ValidateFrom`/`@ValidateTo` + built-ins + `:validators`), `Map<K,V>`, Arrow `Option`/`NonEmptyList`/`NonEmptySet`, converters-{immutable,arrow,datetime,bignumber,uuid,okio,uri}.
- **D (B4) README badges** — top of README: Maven Central version (`kmapper-core`), License (Apache-2.0), Kotlin 2.3.10, KMP platforms.
- **E (B6) Community** — `CONTRIBUTING.md` (build/test via Gradle, PR flow, code style), `ISSUE_TEMPLATE` (bug + feature), `PULL_REQUEST_TEMPLATE.md`, `dependabot.yml` (gradle ecosystem, weekly).

## Finish (orchestrator)
Review agent reports → confirm `.gitignore` excludes `build/` → `git add -A` → grouped/one commit → push via temp gh account switch → restore. Build-infra agent must leave the working tree green (`./gradlew build`/`apiCheck` passing); other agents produce static files only.
