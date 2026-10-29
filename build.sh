#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
jdk_dir="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
build_dir="$sdk_dir/build-tools/${BUILD_TOOLS_VERSION:-36.0.0}"
platform_dir="$sdk_dir/platforms/${MOCI_PLATFORM:-android-37.0}"
mkdir -p .build/bin .build/dex assets dist
"$build_dir/aapt" package -f -M AndroidManifest.xml -S res -A assets -I "$platform_dir/android.jar" -F .build/unsigned.apk
"$jdk_dir/bin/javac" --release 8 -encoding UTF-8 -Xlint:-options -cp "$platform_dir/android.jar" -d .build/bin src/dev/moci/ink/*.java
JAVA_HOME="$jdk_dir" "$build_dir/d8" --lib "$platform_dir/android.jar" --min-api 26 --output .build/dex .build/bin/dev/moci/ink/*.class
cp .build/dex/classes.dex .build/classes.dex
(cd .build && "$build_dir/aapt" add unsigned.apk classes.dex)
"$build_dir/zipalign" -f 4 .build/unsigned.apk .build/aligned.apk
keystore="${MOCI_KEYSTORE:-.build/debug.keystore}"
if [ ! -f "$keystore" ]; then
 "$jdk_dir/bin/keytool" -genkeypair -keystore "$keystore" -storepass android -keypass android -alias moci -dname 'CN=MoCi Development' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
fi
JAVA_HOME="$jdk_dir" "$build_dir/apksigner" sign --v4-signing-enabled false --ks "$keystore" --ks-pass pass:android --key-pass pass:android --out dist/moci-1.2.8.apk .build/aligned.apk
JAVA_HOME="$jdk_dir" "$build_dir/apksigner" verify dist/moci-1.2.8.apk
printf '\nBuilt: dist/moci-1.2.8.apk\n'
