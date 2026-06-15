# Security Policy

## Supported versions

KMapper follows semantic versioning. Security fixes are released for the latest
minor of the current major line.

| Version | Supported          |
|---------|--------------------|
| 2.x     | :white_check_mark: |
| 1.x     | :x:                |

If you are on 1.x, see the [migration guide](docs/guide-en/reference/migration-1x.md)
to move to a supported release.

## Reporting a vulnerability

**Please do not report security vulnerabilities through public GitHub issues, discussions,
or pull requests** — that would disclose the issue before a fix is available.

Instead, report privately through either channel:

- **GitHub Security Advisories** (preferred): open a report at
  <https://github.com/sahsenvar/KMapper/security/advisories/new>. This keeps the
  discussion private until a patch is published.
- **Email**: <s.senvar@joinzad.com> with the subject line `KMapper security`.

Please include enough information to reproduce and assess the issue:

- the affected artifact and version (e.g. `kmapper-compiler:2.0.1`);
- a description of the vulnerability and its impact;
- a minimal reproduction (a failing mapping, generated-code snippet, or build setup);
- any known mitigations or workarounds.

## Disclosure process and timelines

This is a volunteer-maintained open-source project; the following are good-faith targets,
not contractual guarantees:

- **Acknowledgement** within 7 days of your report.
- **Initial assessment** (severity and whether we can reproduce it) within 14 days.
- **Fix or mitigation** for confirmed vulnerabilities as a patch release, typically within
  30 days for high-severity issues, prioritized by impact.

We follow **coordinated disclosure**: please give us a reasonable window to ship a fix
before any public discussion. We will credit reporters in the release notes and the
advisory unless you prefer to remain anonymous.

## Scope

KMapper is a compile-time code generator and runtime library. The most relevant concerns are:

- correctness of generated code that could lead to data exposure or corruption;
- supply-chain integrity of the published artifacts (the release pipeline signs all
  artifacts and publishes provenance via the GitHub Actions workflows in `.github/`);
- vulnerabilities in how the processor handles untrusted annotation input at build time.

Issues in third-party dependencies should be reported upstream, though we are happy to
help coordinate an updated release.
