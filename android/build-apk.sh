#!/usr/bin/env bash
# Build the signed release APK into android/dist/BoomDoom-v<version>.apk
#
#   ./build-apk.sh --v0.2.0              set version 0.2.0 (versionCode +1), then build
#   ./build-apk.sh --version 0.2.0       same
#   ./build-apk.sh --v0.2.0 --install    ...and install on the phone connected over adb
#   ./build-apk.sh --demo                build the demo flavour instead (BoomDoom-v<version>-demo.apk)
#   ./build-apk.sh                       rebuild the current version (asks before overwriting)
#   ./build-apk.sh --create-key          one time: create the release signing key and keystore.properties
#
# The version is written into versionName in app/build.gradle.kts, and versionCode goes up by 1
# (Android only installs an update over an older version when versionCode increases).
set -euo pipefail

PROJECT="$(cd "$(dirname "$0")" && pwd)"
GRADLE_FILE="app/build.gradle.kts"
# Gradle 8.11 cannot run on Android Studio's bundled JDK 25; use JBR 21.
JAVA_HOME="${JAVA_HOME_OVERRIDE:-$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home}"
SDK="$HOME/Library/Android/sdk"
ADB="$SDK/platform-tools/adb"
KEYSTORE="$HOME/.android/boomdoom-release.jks"
export JAVA_HOME

# ---------------------------------------------------------------- arguments
NEW_VERSION=""
INSTALL=false
FLAVOR="prod"
CREATE_KEY=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --install) INSTALL=true ;;
    --demo) FLAVOR="demo" ;;
    --create-key) CREATE_KEY=true ;;
    --version) shift; NEW_VERSION="${1:-}" ;;
    --v*) NEW_VERSION="${1#--v}" ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    *) echo "✗ Unknown option: $1 (try --v0.2.0 or --install)" >&2; exit 1 ;;
  esac
  shift
done
NEW_VERSION="${NEW_VERSION#v}" # accept "v0.2.0" too

cd "$PROJECT"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "✗ Java not found at $JAVA_HOME (set JAVA_HOME_OVERRIDE to a JDK 17-21)." >&2
  exit 1
fi

# ---------------------------------------------------------------- one-time key
if $CREATE_KEY; then
  if [[ -f "$KEYSTORE" || -f keystore.properties ]]; then
    echo "✗ A key already exists ($KEYSTORE or keystore.properties). Not overwriting it:" >&2
    echo "  a new key means phones cannot update the installed app." >&2
    exit 1
  fi
  read -r -s -p "Choose a keystore password (6+ characters): " PASS; echo
  read -r -s -p "Repeat it: " PASS2; echo
  [[ "$PASS" == "$PASS2" && ${#PASS} -ge 6 ]] || { echo "✗ Passwords differ or are too short." >&2; exit 1; }
  mkdir -p "$(dirname "$KEYSTORE")"
  "$JAVA_HOME/bin/keytool" -genkeypair -v -keystore "$KEYSTORE" -alias boomdoom -keyalg RSA -keysize 4096 \
    -validity 10000 -storepass "$PASS" -keypass "$PASS" -dname "CN=BoomDoom, O=BoomDoom" >/dev/null
  umask 077
  printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=boomdoom\nkeyPassword=%s\n' "$KEYSTORE" "$PASS" "$PASS" > keystore.properties
  echo "✓ Created $KEYSTORE and android/keystore.properties (both git-ignored)."
  echo "  BACK BOTH UP NOW. Every future update must be signed with this key."
  exit 0
fi

if [[ ! -f keystore.properties ]]; then
  echo "✗ keystore.properties not found in $PROJECT — the release signing key is missing." >&2
  echo "  First time? Run ./build-apk.sh --create-key. Otherwise restore it and $KEYSTORE from your backup." >&2
  exit 1
fi

# The API address is baked into the app at build time (core/network, boomdoom.apiBaseUrl).
API_URL=$(grep -m1 '^boomdoom.apiBaseUrl=' local.properties 2>/dev/null | cut -d= -f2- || true)
if [[ -z "$API_URL" || "$API_URL" == *"10.0.2.2"* || "$API_URL" == *"localhost"* ]]; then
  echo "✗ boomdoom.apiBaseUrl in local.properties is '${API_URL:-not set}'." >&2
  echo "  A published APK must point at the deployed API, e.g. boomdoom.apiBaseUrl=https://<ip>.sslip.io/" >&2
  exit 1
fi

CUR_VERSION=$(grep -m1 'versionName = ' "$GRADLE_FILE" | sed -E 's/.*"(.*)".*/\1/')
CUR_CODE=$(grep -m1 'versionCode = ' "$GRADLE_FILE" | sed -E 's/[^0-9]*([0-9]+).*/\1/')
SUFFIX=$([[ "$FLAVOR" == "demo" ]] && echo "-demo" || echo "")

# ---------------------------------------------------------------- version bump
if [[ -n "$NEW_VERSION" && "$NEW_VERSION" != "$CUR_VERSION" ]]; then
  if [[ ! "$NEW_VERSION" =~ ^[0-9]+(\.[0-9]+)*$ ]]; then
    echo "✗ Version must look like 0.2.0 (got '$NEW_VERSION')" >&2
    exit 1
  fi
  NEW_CODE=$((CUR_CODE + 1))
  sed -i '' -E "s/versionCode = [0-9]+/versionCode = ${NEW_CODE}/" "$GRADLE_FILE"
  sed -i '' -E "s/versionName = \"[^\"]*\"/versionName = \"${NEW_VERSION}\"/" "$GRADLE_FILE"
  echo "▶ Version ${CUR_VERSION} (code ${CUR_CODE}) → ${NEW_VERSION} (code ${NEW_CODE})"
  VERSION="$NEW_VERSION"
else
  VERSION="$CUR_VERSION"
  if [[ -f "dist/BoomDoom-v${VERSION}${SUFFIX}.apk" ]]; then
    echo "⚠ dist/BoomDoom-v${VERSION}${SUFFIX}.apk already exists and the version isn't changing."
    NEXT="${VERSION%.*}.$(( ${VERSION##*.} + 1 ))"
    echo "  Phones that have it won't take this as an update. Use e.g. ./build-apk.sh --v${NEXT}"
    read -r -p "  Rebuild v${VERSION} anyway and overwrite it? [y/N] " answer
    [[ "$answer" =~ ^[Yy]$ ]] || { echo "Cancelled."; exit 0; }
  fi
fi

# ---------------------------------------------------------------- build
TASK=":app:assemble$(tr '[:lower:]' '[:upper:]' <<< "${FLAVOR:0:1}")${FLAVOR:1}Release"
OUT="dist/BoomDoom-v${VERSION}${SUFFIX}.apk"
echo "▶ Building BoomDoom v${VERSION} (${FLAVOR} release, API ${API_URL})…"
./gradlew "$TASK" -q

APK="app/build/outputs/apk/${FLAVOR}/release/app-${FLAVOR}-release.apk"
[[ -f "$APK" ]] || { echo "✗ Build finished but $APK is missing." >&2; exit 1; }

# Android 15+ requires native libraries aligned to 16 KB pages (ONNX Runtime ships .so files).
ZIPALIGN=$(ls -d "$SDK"/build-tools/*/zipalign 2>/dev/null | sort -V | tail -1 || true)
if [[ -n "$ZIPALIGN" ]]; then
  if "$ZIPALIGN" -c -P 16 4 "$APK" >/dev/null 2>&1; then
    echo "✓ 16 KB page alignment OK"
  else
    echo "✗ The APK is not 16 KB aligned; Android 15+ devices will refuse its native libraries." >&2
    exit 1
  fi
fi

mkdir -p dist
cp "$APK" "$OUT"
SIZE=$(du -h "$OUT" | cut -f1 | tr -d ' ')
echo "✓ Built $PROJECT/$OUT ($SIZE)"

# ---------------------------------------------------------------- install
if $INSTALL; then
  DEVICE=$("$ADB" devices | awk 'NR>1 && $2=="device" && $1 !~ /emulator/ {print $1; exit}')
  if [[ -z "$DEVICE" ]]; then
    echo "✗ No phone connected over adb. Plug it in (or pair Wi-Fi debugging) and try again." >&2
    exit 1
  fi
  echo "▶ Installing on ${DEVICE}…"
  if ! "$ADB" -s "$DEVICE" install -r "$OUT"; then
    echo "✗ Install failed. If the phone has a build from Android Studio, uninstall BoomDoom first" >&2
    echo "  (different signing key), then run this again." >&2
    exit 1
  fi
  echo "✓ Installed"
fi

# ./build-apk.sh --v0.2.0
# ./build-apk.sh --v0.2.0 --install
