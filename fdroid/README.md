# F-Droid

Two ways to get the app through F-Droid.

## This project's repo (GitHub Pages)

Each `v*` tag runs `.github/workflows/release.yml`, which builds the APK, signs it with the
release key, attaches it to a GitHub release and runs `publish.sh` to add it to the F-Droid
repo on the `gh-pages` branch:

    https://bashninja.github.io/ctfd-android/repo?fingerprint=8F92EBDAB6B36391908F27135D4C991E2C2DB1200A1910155C157698ADEB70F3

`publish.sh` takes the listing from `metadata/io.github.bashninja.ctfd.yml` and
`fastlane/metadata/android/`, and signs the index with the repo key (not the APK key).
Dry run against a local checkout:

    PAGES_DIR=/tmp/pages NO_PUSH=1 FDROID_KEYSTORE_B64=... FDROID_KEYSTORE_PASSWORD=... \
      fdroid/publish.sh app/build/outputs/apk/release/app-release.apk

Repository secrets and variables the workflow uses:

| Name | Kind | What |
| --- | --- | --- |
| `RELEASE_KEYSTORE_B64` | secret | `release.jks`, base64 |
| `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD` | secret | its passwords (alias `ctfd`) |
| `FDROID_KEYSTORE_B64` | secret | `fdroid-repo.jks`, base64 (alias `fdroid`) |
| `FDROID_KEYSTORE_PASSWORD` | secret | its store and key password |
| `RELEASE_CERT_SHA256` | variable | the APK certificate's SHA-256, checked before publishing |

Both keystores are kept in `~/.config/ctfd-android/`. Losing the release key means
installed copies can't be updated; losing the repo key means everyone re-adds the repo.

## The official F-Droid repo

`fdroiddata/io.github.bashninja.ctfd.yml` is the build recipe for
[fdroiddata](https://gitlab.com/fdroid/fdroiddata), in `fdroid rewritemeta` format and
clean under `fdroid lint`. To submit: fork fdroiddata, copy it to `metadata/`, run
`fdroid build -v -l io.github.bashninja.ctfd` if you can, and open a merge request.

The recipe asks for a reproducible build (`Binaries` and `AllowedAPKSigningKeys`): when
F-Droid's build matches the GitHub release, it ships the APK signed with this project's
key, so installs from either repo or from GitHub update each other. If the reviewers can't
reproduce it, remove those two keys and F-Droid signs with its own key.

For each release, F-Droid's checkupdates picks up new `v*` tags (`UpdateCheckMode: Tags`)
and reads `versionName` and `versionCode` from `app/build.gradle.kts`, so bump both and
add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` before tagging.
