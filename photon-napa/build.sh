#!/usr/bin/env bash
# Build libphoton-napa.so, the NAPA CPU AprilTag detector (by Zachary Wilson, GPL-3.0-only) for
# PhotonVision's AprilTag pipeline ("Detector: NAPA"), and run its self-test. Run it on the coprocessor
# itself (Orange Pi 5 / RK3588, Raspberry Pi, any aarch64 or x86_64 Linux): plain gcc/g++ and a JDK's
# jni.h, no OpenCV, WPILib or CUDA.
#
#   photon-napa/build.sh [--install] [OUT]      OUT defaults to ./build (next to this script)
#
# --install copies the library to /usr/lib (on Java's default java.library.path) with sudo. Restart
# PhotonVision afterwards (sudo systemctl restart photonvision). Without the library PhotonVision keeps
# using AprilTag 3 and says so in the AprilTag tab.
#
# Environment overrides:
#   CC, CXX          compilers (default gcc, g++); cross builds also set TARGET_ARCH (aarch64, x86_64)
#   ARCH_FLAGS       CPU flags (default: picked from the board, see below)
#   JAVA_HOME        a JDK with include/jni.h (default: the first one found under /usr/lib/jvm)
set -euo pipefail

INSTALL=0
if [[ ${1:-} == "--install" ]]; then
  INSTALL=1
  shift
fi
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=${1:-$HERE/build}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)

CC=${CC:-gcc}
CXX=${CXX:-g++}
ARCH=${TARGET_ARCH:-$(uname -m)}

need() { command -v "$1" >/dev/null 2>&1 || { echo "missing '$1': sudo apt install $2" >&2; exit 1; }; }
need "$CC" build-essential
need "$CXX" build-essential
need patch patch

# jni.h: any JDK's works (the library only asks for JNI 1.6).
if [[ -z ${JAVA_HOME:-} || ! -f ${JAVA_HOME}/include/jni.h ]]; then
  JAVA_HOME=""
  for d in /usr/lib/jvm/*/; do
    if [[ -f $d/include/jni.h ]]; then
      JAVA_HOME=${d%/}
      break
    fi
  done
fi
if [[ -z $JAVA_HOME ]]; then
  echo "no JDK headers (include/jni.h) found: install a JDK, e.g. 'sudo apt install openjdk-17-jdk-headless'," >&2
  echo "or set JAVA_HOME to one" >&2
  exit 1
fi
JNI_OS=linux
[[ -d $JAVA_HOME/include/$JNI_OS ]] || { echo "no $JAVA_HOME/include/$JNI_OS" >&2; exit 1; }

# CPU flags. RK3588/RK3588S (Orange Pi 5 family): tune for its Cortex-A76 + A55 cores (both ARMv8.2-A).
if [[ -z ${ARCH_FLAGS+x} ]]; then
  case $ARCH in
    aarch64)
      if [[ $ARCH == "$(uname -m)" ]] && tr -d '\0' </proc/device-tree/compatible 2>/dev/null | grep -q 'rk3588'; then
        ARCH_FLAGS="-mcpu=cortex-a76.cortex-a55"
      else
        ARCH_FLAGS="-march=armv8-a+simd"
      fi
      ;;
    x86_64) ARCH_FLAGS="-msse4.1" ;;  # NAPA's SSE path needs SSSE3
    *) ARCH_FLAGS="" ;;
  esac
fi

# NAPA v0.5.1 as vendored, plus SpectrumJetson's patches (early-decode slack, speed, and the streaming
# API napa_far.cc links against; none changes napa_detect's results), in name order.
SRC=$OUT/napa-src
rm -rf "$SRC"
mkdir -p "$SRC"
cp -r "$HERE/third_party/napa/." "$SRC/"
for p in "$HERE"/patches/napa-*.patch; do
  patch -s -p3 -d "$SRC" --no-backup-if-mismatch <"$p" || { echo "NAPA patch failed: $p" >&2; exit 1; }
done

CF="-O3 -fPIC -fvisibility=hidden -fno-wrapv $ARCH_FLAGS"
echo "building for $ARCH with: $CF"
$CC $CF -std=gnu99 -c "$SRC/napa_core.c" -I"$SRC" -o "$OUT/napa_core.o"
$CXX $CF -std=c++17 -Wall -Wextra -c "$HERE/src/napa_far.cc" -I"$SRC" -I"$HERE/src" -o "$OUT/napa_far.o"
$CXX $CF -std=c++17 -Wall -Wextra -c "$HERE/src/NapaDetectorJNI.cc" -I"$SRC" -I"$HERE/src" \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/$JNI_OS" -o "$OUT/NapaDetectorJNI.o"
$CXX -shared -Wl,-z,defs -Wl,-soname,libphoton-napa.so -o "$OUT/libphoton-napa.so" \
  "$OUT/napa_core.o" "$OUT/napa_far.o" "$OUT/NapaDetectorJNI.o" -lm -lpthread

# Self-test: renders tags with NAPA's own codes and checks ids, corner order and convention, the
# homography and the far search.
$CXX -O2 $ARCH_FLAGS -std=c++17 -Wall "$HERE/src/napa_test.cc" "$OUT/napa_far.o" "$OUT/napa_core.o" \
  -I"$SRC" -I"$HERE/src" -lm -lpthread -o "$OUT/napa_test"
if [[ $ARCH == "$(uname -m)" ]]; then
  "$OUT/napa_test"
else
  echo "cross build for $ARCH: run $OUT/napa_test on the target"
fi
echo "built $OUT/libphoton-napa.so ($ARCH)"

if [[ $INSTALL == 1 ]]; then
  sudo install -m 755 "$OUT/libphoton-napa.so" /usr/lib/libphoton-napa.so
  echo "installed /usr/lib/libphoton-napa.so; now: sudo systemctl restart photonvision"
fi
