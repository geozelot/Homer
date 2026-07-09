#!/usr/bin/env bash
# Compile-check Homer before handoff.
# Android Studio installed a merged JDK17 + Android SDK at ~/.jdks/corretto-17.0.19;
# the default `java` on PATH is 11 (too old for AGP), so pin JAVA_HOME.
set -euo pipefail
export JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/corretto-17.0.19}"
cd "$(dirname "$0")/.."
exec ./gradlew :app:assembleDebug --console=plain "$@"
