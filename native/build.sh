#!/usr/bin/env bash
set -euo pipefail

: "${EXIFTOOL_VERSION:?EXIFTOOL_VERSION is required}"
: "${PERLCROSS_VERSION:?PERLCROSS_VERSION is required}"
: "${NDK_VERSION:?NDK_VERSION is required}"
: "${ABI:?ABI is required}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${ROOT}/app/src/main/jniLibs/${ABI}"
mkdir -p "${OUT}"

case "${ABI}" in
  arm64-v8a) TARGET="aarch64-linux-android" ;;
  armeabi-v7a) TARGET="arm-linux-androideabi" ;;
  x86_64) TARGET="x86_64-linux-android" ;;
  x86) TARGET="i686-linux-android" ;;
  *) echo "Unsupported ABI: ${ABI}" >&2; exit 2 ;;
esac

echo "Preparing ExifTool ${EXIFTOOL_VERSION} for ${ABI} (${TARGET})"
echo "Pinned perl-cross: ${PERLCROSS_VERSION}; NDK: ${NDK_VERSION}"

# Native artifacts are deliberately not checked in as opaque binaries.
# The CI workflow is responsible for provisioning the pinned toolchain and
# must place the generated executable at the path below.
test -f "${OUT}/libexiftool.so" || {
  echo "Missing native artifact: ${OUT}/libexiftool.so" >&2
  echo "Provision the pinned ExifTool/perl-cross build before assembling the APK." >&2
  exit 3
}
