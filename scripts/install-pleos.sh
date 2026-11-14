#!/bin/bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
adb_binary="$sdk_root/platform-tools/adb"
apk_path="$repo_root/app/build/intermediates/apk/debug/app-debug.apk"
avd_target="Pleos_Connect_API_34"
scenario="${1:-NORMAL}"

if [[ ! -x "$adb_binary" ]]; then
    printf 'ADB를 찾을 수 없습니다: %s\n' "$adb_binary" >&2
    exit 1
fi
if [[ ! -f "$apk_path" ]]; then
    printf 'APK를 먼저 빌드하세요: 저장소에서 ./gradlew :app:assembleDebug\n' >&2
    exit 1
fi

device_list="$("$adb_binary" devices)"
pleos_serial=""
while read -r serial status rest; do
    [[ "$serial" == emulator-* && "$status" == device ]] || continue
    if ! avd_name="$("$adb_binary" -s "$serial" emu avd name 2>/dev/null)"; then
        continue
    fi
    avd_name="${avd_name%%$'\n'*}"
    avd_name="${avd_name%$'\r'}"
    [[ "$avd_name" == "$avd_target" ]] || continue
    if [[ -n "$pleos_serial" ]]; then
        printf 'Pleos가 둘 이상 실행 중입니다. 하나만 실행한 뒤 다시 시도하세요.\n' >&2
        exit 1
    fi
    pleos_serial="$serial"
done <<< "$device_list"

if [[ -z "$pleos_serial" ]]; then
    printf '실행 중인 Pleos를 찾지 못했습니다. 아래 명령으로 켜고 부팅이 끝난 뒤 다시 실행하세요.\n' >&2
    printf '"%s/emulator/emulator" -avd %s -no-snapshot-load\n' "$sdk_root" "$avd_target" >&2
    exit 1
fi

printf 'Pleos 확인: %s (%s)\n' "$avd_target" "$pleos_serial"
"$adb_binary" -s "$pleos_serial" install -r -t "$apk_path"
"$adb_binary" -s "$pleos_serial" shell am start -S \
    -n com.codrive/.app.DrivingStatusActivity --es demo_scenario "$scenario"
