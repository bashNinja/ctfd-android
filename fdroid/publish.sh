#!/usr/bin/env bash
# publish.sh APK: add a signed release APK to the F-Droid repo on the gh-pages branch.
#
# Needs FDROID_KEYSTORE_B64 and FDROID_KEYSTORE_PASSWORD (the repo index signing key, not
# the APK key), and GH_TOKEN to push. Set PAGES_DIR to work in an existing checkout and
# NO_PUSH=1 to stop before pushing, for a dry run.
set -euo pipefail

apk=$(realpath "$1")
src=$(cd "$(dirname "$0")/.." && pwd)
app=io.github.miketweaver.ctfd
work=${RUNNER_TEMP:-$(mktemp -d)}
pages=${PAGES_DIR:-$work/pages}
remote="https://github.com/${GITHUB_REPOSITORY:-miketweaver/ctfd-android}.git"

if [ ! -d "$pages/.git" ]; then
  if git ls-remote --exit-code --heads "$remote" gh-pages >/dev/null; then
    git clone --quiet --depth 1 --branch gh-pages "$remote" "$pages"
  else
    git init --quiet -b gh-pages "$pages"
    git -C "$pages" remote add origin "$remote"
  fi
fi

# fdroidserver in a venv; Homebrew's Python on the runner, since there is no system pip.
python=$(command -v /home/linuxbrew/.linuxbrew/bin/python3.13 || command -v python3)
venv="${RUNNER_TOOL_CACHE:-$work}/fdroidserver-venv"
[ -x "$venv/bin/fdroid" ] || { "$python" -m venv "$venv" && "$venv/bin/pip" install --quiet fdroidserver; }

umask 077
echo "$FDROID_KEYSTORE_B64" | base64 -d > "$work/fdroid-repo.jks"
umask 022

cd "$pages"
cat > config.yml <<CONFIG
repo_url: https://miketweaver.github.io/ctfd-android/repo
repo_name: CTFd for Android
repo_description: >-
  Builds of CTFd for Android, signed by its author and published from
  https://github.com/miketweaver/ctfd-android on each release.
repo_icon: icon.png
archive_older: 0
sdk_path: ${ANDROID_HOME:-$HOME/Android/Sdk}
keystore: $work/fdroid-repo.jks
repo_keyalias: fdroid
keystorepass: {env: FDROID_KEYSTORE_PASSWORD}
keypass: {env: FDROID_KEYSTORE_PASSWORD}
CONFIG
chmod 600 config.yml
printf 'config.yml\ntmp/\n' > .gitignore
touch .nojekyll

# Listing metadata from the source tree; fdroid reads the fastlane layout under metadata/<app>/.
rm -rf metadata && mkdir -p metadata/$app config repo
cp "$src/fdroid/metadata/$app.yml" metadata/
cp -r "$src/fastlane/metadata/android/." metadata/$app/
# fdroid copies the repo icon from here into repo/icons/.
cp "$src/fastlane/metadata/android/en-US/images/icon.png" icon.png
# Categories a repo may use are declared in config/; these match F-Droid's official names.
printf 'Security:\n  name: Security\nScience & Education:\n  name: Science & Education\n' > config/categories.yml

version_code=$("$venv/bin/python" -I -c 'import sys
from androguard.core.apk import APK
print(APK(sys.argv[1]).get_androidversion_code())' "$apk" 2>/dev/null)
cp "$apk" "repo/${app}_${version_code}.apk"

"$venv/bin/fdroid" update --pretty
"$venv/bin/fdroid" lint

git add -A
git -c user.name="github-actions[bot]" -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
  commit --quiet -m "Publish $app $version_code"
if [ "${NO_PUSH:-0}" != 1 ]; then
  git push --quiet "https://x-access-token:${GH_TOKEN}@${remote#https://}" HEAD:gh-pages
fi
echo "published $app $version_code to $pages"
