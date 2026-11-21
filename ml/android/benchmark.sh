#!/usr/bin/env bash
# Run exported models in a standalone ART process using only the supplied test video.
set -euo pipefail
if [ "$#" -ne 6 ]; then
    echo "Usage: benchmark.sh DEVICE RUNTIME.aar EXTENSIONS.aar MODEL_DIRECTORY VIDEO.mp4 REPEATS" >&2
    exit 2
fi
device=$1
runtime=$2
ext=$3
models=$4
video=$5
repeats=$6
[[ "$repeats" =~ ^[1-9][0-9]*$ ]] || { echo "REPEATS must be positive" >&2; exit 2; }
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
adb="$ANDROID_HOME/platform-tools/adb"
android="$ANDROID_HOME/platforms/android-35/android.jar"
d8="$ANDROID_HOME/build-tools/34.0.0/d8"
here="$(cd "$(dirname "$0")" && pwd)"
build="$(mktemp -d)"
remote=""
cleanup() {
    if [[ "$remote" == /data/local/tmp/boomdoom-bench.* ]]; then
        "$adb" -s "$device" shell rm -rf "$remote"
    fi
    rm -rf "$build"
}
trap cleanup EXIT
unzip -q "$runtime" classes.jar 'jni/arm64-v8a/*' -d "$build"
unzip -q "$ext" 'jni/arm64-v8a/libortextensions.so' -d "$build"
mkdir -p "$build/classes"
javac --release 8 -classpath "$android:$build/classes.jar" -d "$build/classes" "$here/ReelBenchmark.java"
jar cf "$build/runner.jar" -C "$build/classes" .
"$d8" --min-api 28 --lib "$android" --output "$build/bench.jar" "$build/runner.jar" "$build/classes.jar"
remote="$("$adb" -s "$device" shell mktemp -d /data/local/tmp/boomdoom-bench.XXXXXXXX | tr -d '\r')"
"$adb" -s "$device" push "$build/bench.jar" "$build/jni/arm64-v8a/"*.so "$models/vision.onnx" "$models/tokenizer.onnx" "$models/caption.onnx" "$models/vision.json" "$models/caption.json" "$remote/" >&2
"$adb" -s "$device" push "$video" "$remote/sample.mp4" >&2
"$adb" -s "$device" shell chmod 444 "$remote/bench.jar"
# Generate the synthetic ImageReader producer input from the same team clip.
"${PYTHON:-python3}" - "$video" "$build/frame.png" <<'PYFRAME'
import av
import sys
with av.open(sys.argv[1]) as video:
    next(video.decode(video=0)).to_image().save(sys.argv[2])
PYFRAME
"$adb" -s "$device" push "$build/frame.png" "$remote/frame.png" >&2
for stage in caption image_reader one_frame three_frames; do
    "$adb" -s "$device" shell "CLASSPATH=$remote/bench.jar app_process -Djava.library.path=$remote:/system/lib64 /system/bin app.boomdoom.bench.ReelBenchmark $remote $stage $repeats"
done
