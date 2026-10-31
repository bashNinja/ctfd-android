# CTFd for Android

Play a [CTFd](https://ctfd.io) capture-the-flag from your phone: your place and score,
the challenge board, challenge descriptions, files and hints, flag submission, the
scoreboard and what other players have solved. It is the phone counterpart of the
[`ctfd` CLI](https://github.com/miketweaver/homebrew-commandline-tools) and talks to the same REST
API with a personal access token.

## Installing

- **F-Droid**: add this repo in the F-Droid app (Settings › Repositories, or open the
  link on the phone), then install *CTFd for Android*:
  `https://miketweaver.github.io/ctfd-android/repo?fingerprint=8F92EBDAB6B36391908F27135D4C991E2C2DB1200A1910155C157698ADEB70F3`
- **APK**: download it from [Releases](https://github.com/miketweaver/ctfd-android/releases).

Both are signed with the same key, so either one updates the other.

## Using it

1. On the CTF's site, create a token under **Settings › Access Tokens**.
2. Open the app, enter the CTF's address (`ctf.example.org`) and paste the token.
   The app checks it with `/api/v1/users/me` before saving it.
3. Switch CTFs, add more or remove one from the menu under the CTF name at the top.

- **Challenges**: grouped by category in CTFd's order, with search and
  All / Unsolved / Solved filters. Pull down to refresh.
- **Challenge**: the description (Markdown), connection info with a copy button, files
  (saved to `Downloads/ctfd/<ctf>/<challenge>/`), hints, attempts left, and a flag box.
- **Scoreboard**: your row is highlighted; tap anyone to see their solves.

Each flag is sent once per tap and never retried. Wrong flags count against any attempt
limit, and CTFd rate-limits about 10 a minute. Unlocking a hint asks first and shows its
cost, because CTFd takes the points off your score.

## Tokens

Tokens are encrypted with an AES key kept in the Android Keystore, and app data is
excluded from backups. File downloads use CTFd's per-file token in the URL, so the API
token is never sent to the storage host a hosted CTFd redirects downloads to. Only https
is allowed, except `10.0.2.2`, `localhost` and `127.0.0.1` for a CTFd running on the
development machine.

## Building

Needs JDK 21 and the Android SDK (`local.properties` with `sdk.dir`, or `ANDROID_HOME`).
The Gradle wrapper downloads Gradle 9.8, and AGP installs build-tools and platform 37.

```sh
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release

`./gradlew assembleRelease bundleRelease` writes a signed, shrunk APK
(`app/build/outputs/apk/release/app-release.apk`) and an App Bundle for Play. The signing
key is read from `~/.config/ctfd-android/keystore.properties` (or the file named by
`CTFD_KEYSTORE_PROPERTIES`):

```properties
storeFile=/home/you/.config/ctfd-android/release.jks
storePassword=...
keyAlias=ctfd
keyPassword=...
```

Without it the release build is unsigned. Keep the key backed up: updates to an installed
app must be signed with the same key.

### Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Commit, then tag and push: `git tag v0.2.0 && git push origin main v0.2.0`.

The `release` workflow builds and signs the APK, creates the GitHub release and updates
the F-Droid repo. See [fdroid/README.md](fdroid/README.md) for the secrets it needs and the
official F-Droid submission.

## CI

`test` (every push to `main` and every pull request) runs the unit tests and builds the
debug and release APKs and lint, on the self-hosted runner (`runs-on: [self-hosted, Linux, X64]`).
The job gets its own `HOME`; the Android SDK and Gradle caches live under
`$RUNNER_TOOL_CACHE` so they download once. The runner instance for this repository is
registered from a `commandline-tools` checkout with `scripts/runner-add-repo.sh ctfd-android`.

### Build troubleshooting

If the build fails with `Could not determine Java version from '[0.001s][warning][os,container] Cgroup ...`,
the JVM is printing container warnings into `jlink --version`. Silence them and restart
the daemon:

```sh
./gradlew --stop
export JAVA_TOOL_OPTIONS="-Xlog:disable -Xlog:all=error:stderr"
```

## License

BSD 3-Clause; see [LICENSE](LICENSE). Not affiliated with CTFd LLC.
