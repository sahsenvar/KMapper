# Publishing Runbook — Maven Central (Central Portal)

This document covers the **one-time manual setup** (Steps 1–5) required before automated
publishing can succeed — whether tag-driven from CI (the primary path, see Step 6) or run
locally as a fallback. None of the setup steps can be automated — they require a browser,
GPG tooling, and your local `~/.gradle/gradle.properties` file.

---

## Prerequisites

- JDK 11+ with `gpg2` (or `gpg`) on PATH
- Gradle wrapper (`./gradlew`) working locally
- GitHub account: `sahsenvar`

---

## Step 1 — Create a Central Portal Account and Verify the Namespace

1. Go to <https://central.sonatype.com> and sign in (or create an account).
2. Navigate to **Namespaces** → **Add Namespace**.
3. Enter `io.github.sahsenvar` and click **Verify Namespace**.
4. Central Portal will show a **verification code** (a short string like `abc123xyz`).
5. Create a **public GitHub repository** under the `sahsenvar` account named exactly that
   verification code (e.g. `https://github.com/sahsenvar/abc123xyz`). An empty repo is fine.
6. Back in Central Portal, click **Verify** — it queries GitHub to confirm the repo exists.
7. Once verified, delete the temporary repository if you like.

---

## Step 2 — Generate a Publishing User Token

1. In Central Portal, go to **Account** → **Generate User Token**.
2. Copy the **Username** and **Password** values — these are your publishing credentials.
   They are NOT your login credentials; they are one-time-generated tokens.
3. Store them (see Step 5 below — do NOT put them in the repo).

---

## Step 3 — Generate a GPG Key

```bash
# Generate (RSA 4096 recommended)
gpg2 --full-generate-key
# Key type: RSA and RSA, 4096 bits, no expiry (or choose an expiry)
# Real name: Şahan Şenvar
# Email: s.senvar@joinzad.com

# List keys to find your KEY_ID (last 8 hex chars of the fingerprint)
gpg2 --list-secret-keys --keyid-format SHORT

# Publish the public key to a keyserver (required by Maven Central)
gpg2 --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
# Also send to keys.openpgp.org and pgp.mit.edu for redundancy:
gpg2 --keyserver keys.openpgp.org --send-keys <KEY_ID>
gpg2 --keyserver pgp.mit.edu --send-keys <KEY_ID>

# Export the ASCII-armored secret key for in-memory signing
gpg2 --export-secret-keys --armor <KEY_ID>
```

The exported secret key block looks like:

```
-----BEGIN PGP PRIVATE KEY BLOCK-----
...many lines...
-----END PGP PRIVATE KEY BLOCK-----
```

For the `signingInMemoryKey` Gradle property, take the exported block and:
- Remove the first line (`-----BEGIN PGP PRIVATE KEY BLOCK-----`)
- Remove the last two lines (the checksum line and `-----END PGP PRIVATE KEY BLOCK-----`)
- Remove ALL line breaks so it is a single continuous string

---

## Step 4 — Store Secrets in `~/.gradle/gradle.properties`

Open (or create) `~/.gradle/gradle.properties` — this file is **never committed** to the repo.

Add the following properties:

```properties
# Maven Central Portal credentials (from Step 2)
mavenCentralUsername=<your-central-portal-token-username>
mavenCentralPassword=<your-central-portal-token-password>

# GPG in-memory signing (from Step 3)
# signingInMemoryKeyId is optional but recommended (last 8 chars of fingerprint)
signingInMemoryKeyId=<KEY_ID_8_CHARS>
# Single-line export of the secret key (no headers, no line breaks)
signingInMemoryKey=<single-line-armored-secret-key>
# Omit signingInMemoryKeyPassword if the key was created without a passphrase
signingInMemoryKeyPassword=<passphrase-or-leave-blank>
```

**Exact property names** (verified against vanniktech 0.33.0 source):

| Property | Purpose |
|---|---|
| `mavenCentralUsername` | Central Portal user token username |
| `mavenCentralPassword` | Central Portal user token password |
| `signingInMemoryKey` | ASCII-armored secret key (single line, no headers) |
| `signingInMemoryKeyId` | Short key ID (8 hex chars) — optional but recommended |
| `signingInMemoryKeyPassword` | Key passphrase — omit if key has no passphrase |

Alternatively, these can be set as environment variables with the prefix `ORG_GRADLE_PROJECT_`
(e.g. `ORG_GRADLE_PROJECT_signingInMemoryKey`) for CI environments.

---

## Step 5 — Local Verification (No Credentials Required)

Before a real release, verify the artifacts are correct with a local publish:

```bash
./gradlew publishToMavenLocal --no-configuration-cache
```

Check that artifacts appear under:

```
~/.m2/repository/io/github/sahsenvar/
  kmapper-core/<version>/
  kmapper-core-jvm/<version>/
  kmapper-core-android/<version>/
  kmapper-core-iosarm64/<version>/
  kmapper-core-iossimulatorarm64/<version>/
  kmapper-processor/<version>/
  kmapper-converters-immutable/<version>/
  ...
```

---

## Step 6 — Release to Maven Central

The primary release path is **tag-driven CI**: pushing a `vX.Y.Z` tag triggers
`.github/workflows/publish.yml`, which runs `./gradlew publishAndReleaseToMavenCentral` and
releases the deployment on the Central Portal automatically — no manual portal interaction and
no local credentials needed. CI reads its credentials from the repository's GitHub Actions
secrets (`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_IN_MEMORY_KEY`,
`SIGNING_IN_MEMORY_KEY_ID`, `SIGNING_IN_MEMORY_KEY_PASSWORD`), which mirror the Step 4
properties via the `ORG_GRADLE_PROJECT_` prefix.

1. **Bump the version** in `build.gradle.kts` (root `allprojects` block):
   - Change `0.1.0-SNAPSHOT` → `0.1.0`
   - SNAPSHOT versions are deployed to the Central Portal staging area and are not released
     to consumers automatically.

2. **Commit and push** the release commit to `main`, then **tag it and push the tag**:

```bash
git tag v0.1.0
git push origin v0.1.0
```

   The tag push triggers two independent workflows:
   - `publish.yml` — publishes all 11 modules and releases them to Maven Central.
   - `release.yml` — creates the GitHub Release with the matching `CHANGELOG.md` section.

3. **Bump to next SNAPSHOT** in `build.gradle.kts`:
   - Change `0.1.0` → `0.2.0-SNAPSHOT` (or whichever next version)
   - Commit and push.

### Fallback — manual release from a local machine

If CI is unavailable, release locally instead (requires the Step 4 credentials in
`~/.gradle/gradle.properties`):

```bash
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
```

This uploads the artifacts, monitors the deployment status, and releases them automatically
once all checks pass (POM completeness, signing, Javadoc presence).

> **Warning:** after a manual release, do **not** push a `v*` tag for that version — the tag
> would trigger `publish.yml` for a version that is already released, and the CI job will fail
> on the duplicate Central Portal deployment. If you still want the tag (e.g. for the GitHub
> Release created by `release.yml`), push it knowing the publish job's failure is expected and
> harmless in that case.

---

## Troubleshooting

- **Namespace not verified**: Ensure the GitHub verification repo was public at the time of
  verification. Re-verify in Central Portal if the repo was temporarily private.
- **Signing failure**: Confirm `signingInMemoryKey` is a single line with no headers or breaks.
  Re-export with `gpg2 --export-secret-keys --armor <KEY_ID>` and strip manually.
- **401 Unauthorized**: Regenerate the User Token in Central Portal — tokens can expire or be
  invalidated. Update `mavenCentralUsername` / `mavenCentralPassword` accordingly.
- **Deployment validation timeout**: The default validation mode is `PUBLISH`. Large multi-platform
  publications may take several minutes. The plugin polls automatically; wait for it to complete.
