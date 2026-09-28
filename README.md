# Today

An offline, encrypted, immutable journal for Android.

Write one entry a day. When the day rolls over, that entry is sealed and can never
be changed again — not by the app, not by a database trigger, and not by moving the
device clock backwards.

There is no account, no server, no analytics, and no `INTERNET` permission. Your
writing never leaves the device.

---

## What makes it different

Most journaling apps let you edit yesterday. Today assumes you will want to change
yesterday, and so it quietly allows it. This one does not, and treats that as the
central design constraint rather than a setting.

The guarantee is enforced at four independent layers, because a single layer is a
single point of failure:

| Layer | What it does |
|---|---|
| Repository | The only write path. Refuses to save or delete a sealed entry. |
| Database | A trigger rejects any `UPDATE` that would change a locked row. |
| Lock flag | A sealed entry has no code path that clears `locked_at`. |
| Clock | The effective journal day is monotonic — it never moves backwards. |

The clock layer is the interesting one. A naive implementation compares the stored
date against `LocalDate.now()`, which means setting the device clock back a week
would make a week of sealed entries editable again. Instead the app tracks the
highest day it has ever observed, persists it, and refuses to go below it. Moving
the clock back is detected and reported to you rather than acted on silently.

## Privacy

- Entry bodies are encrypted with AES-256-GCM before they reach storage. The key
  lives in the Android Keystore, wrapped by hardware where the device supports it.
- Search runs over sealed SHA-256 token fingerprints, so the database never has to
  be decrypted in full to find something. Candidates are verified against the real
  text only after they are narrowed down.
- Cloud backup and device-to-device transfer are both **disabled**. A transferred
  database would be ciphertext whose key stayed behind, so it would be useless to
  you and it would move private data off the device. The in-app export is the only
  supported way to move a journal.
- No storage permission is requested. The app only touches the single file you pick
  through the system file picker.
- Optional PIN or biometric lock, and an option to block screenshots.

## Features

- Rich text: headings, quotes, callouts, checklists, code, tables, dividers, links,
  highlights, images
- Word count, reading time, and time-spent-writing per entry
- Calendar with per-day write status
- Statistics: streaks, monthly activity, tag frequency
- Daily writing prompts, chosen locally
- Export to Markdown, plain text, HTML, or JSON
- Import with a **review step**: a backup is read and summarised before anything is
  written, and an entry you already sealed is never replaced
- Light, dark, AMOLED, and high-contrast themes with a custom accent
- Reduce motion, text scaling, and larger touch targets
- English only, by design

## Requirements

- Android 8.0 (API 26) or newer
- JDK 17
- Android SDK 36

## Building

```bash
# Point the build at your SDK
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # static analysis
./gradlew assembleRelease        # signed release APKs, one per architecture
```

`assembleRelease` writes five APKs to `app/build/outputs/apk/release/`:

| File | Architecture |
|---|---|
| `app-arm64-v8a-release.apk` | Modern phones (most devices since ~2017) |
| `app-armeabi-v7a-release.apk` | Older 32-bit phones |
| `app-x86_64-release.apk` | 64-bit emulators |
| `app-x86-release.apk` | 32-bit emulators |
| `app-universal-release.apk` | Any device |

The app currently ships no native libraries, so these hold the same bytecode.
The split is kept because it costs nothing and any future native dependency
would otherwise ship every architecture to every device.

## Signing a release

Signing is opt-in and the keystore is never committed. Provide either a
git-ignored `keystore.properties` in the project root:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

…or the matching environment variables, which is what CI uses:

```
TODAY_STORE_FILE
TODAY_STORE_PASSWORD
TODAY_KEY_ALIAS
TODAY_KEY_PASSWORD
```

With neither, `assembleRelease` still succeeds and produces an unsigned APK.

To create a key:

```bash
keytool -genkeypair -v -keystore release.jks -storetype PKCS12 \
  -alias today-release -keyalg RSA -keysize 4096 -sigalg SHA256withRSA \
  -validity 36500
```

**Back up `release.jks` and its passwords.** An app can only be updated in
place by a build signed with the same key, so losing both ends the ability to
ship updates.

## Continuous integration

`.github/workflows/build.yml` has three jobs:

- **Verify** — unit tests and lint, on every push and pull request. No secrets
  are exposed to untrusted forks.
- **Build release APKs** — assembles the five signed APKs, the R8 `mapping.txt`,
  and a `SHA256SUMS` file, and uploads them as a workflow artifact.
- **Release** — tagged pushes (`v*`) attach those same artifacts to the
  [GitHub release page](https://github.com/XII777/today/releases).

Signing in CI needs these repository secrets:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | the key alias |
| `KEY_PASSWORD` | the key password |

Without them the pipeline still builds, but the APKs are unsigned and the
workflow says so explicitly. An unsigned APK installs but can never be upgraded
in place.

To publish:

```bash
git tag v1.0.0
git push origin v1.0.0
```


## Architecture

A hand-written dependency container, no DI framework. For a graph this size it is
smaller, easier to audit, and cheaper to build than generated code.

```
core/       date arithmetic, the monotonic clock, text metrics
crypto/     Keystore-backed AES-GCM
data/       Room, the repository, the search index, import/export
domain/     models and repository contracts
security/   PIN hashing, app lock
ui/         Compose screens, one package per destination
```

Two decisions worth calling out:

**Encryption is at the application layer, not SQLCipher.** Each field is sealed
before it is written. This costs a little throughput and buys two things: the search
index can be built from sealed fingerprints, and the domain and integrity logic stay
testable on the JVM with no native dependency. It is a deliberate deviation from a
database-level encryption spec.

**Search is exact-token, not stemmed.** Searching `garden` will not match
`gardening`. Fingerprint collisions are handled by verifying candidates against the
decrypted text; stem matching would need a different index, and exact matching is
more precise for the way people actually search their own writing.

## Tests

```bash
./gradlew testDebugUnitTest
```

The unit tests cover the parts where a bug is silent and permanent: text-range
maintenance through edits, the monotonic clock's behaviour under clock tampering,
integrity hashing, document serialisation round-trips, and the search index.

## License

MIT. See [LICENSE](LICENSE).
