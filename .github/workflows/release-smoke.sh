#!/usr/bin/env bash
# Smoke-тест минифицированного release на эмуляторе (job release-smoke в build.yml).
# Ловит то, чего не видят unit-тесты: падения после R8 (пропавшие сериализаторы маршрутов Navigation,
# Room _Impl, Hilt). Шаги: установка → холодный старт → Главная → «Новая игра» → «Начать игру» → стол.
# Падение процесса на любом шаге — ошибка; не найденная кнопка на шаге «стол» — предупреждение
# (UI мог измениться, это не R8). Логи и снимок экрана — в каталог $2 (артефакт job).
#
#   release-smoke.sh <signed-release.apk> <out-dir>
set -euo pipefail

apk="$1"
out="$2"
pkg="app.feltbook"
mkdir -p "$out"
ui="$out/ui.xml"

collect() {
  adb logcat -d > "$out/logcat.txt" 2>&1 || true
  adb logcat -d -b crash > "$out/crash.txt" 2>&1 || true
  adb exec-out screencap -p > "$out/screen.png" 2>/dev/null || true
}

fail() {
  echo "::error::release-smoke: $*"
  collect
  exit 1
}

# Процесс жив и в crash-буфере нет его FATAL EXCEPTION.
check_alive() {
  local step="$1"
  if adb logcat -d -b crash | grep -q "Process: $pkg,"; then
    adb logcat -d -b crash | grep -A40 "Process: $pkg," | head -60
    fail "$step: процесс упал (FATAL EXCEPTION), стек выше и в crash.txt"
  fi
  adb shell pidof "$pkg" > /dev/null || fail "$step: процесса $pkg нет (pidof)"
  echo "ok: $step"
}

dump_ui() {
  local i
  for i in 1 2 3 4 5; do
    if adb shell uiautomator dump /sdcard/feltbook-ui.xml > /dev/null 2>&1 &&
      adb pull /sdcard/feltbook-ui.xml "$ui" > /dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# Центр первого узла: text/content-desc содержит $1; "@empty-edit" — пустое поле ввода.
find_center() {
  python3 - "$ui" "$1" << 'PY'
import re, sys
import xml.etree.ElementTree as ET
path, needle = sys.argv[1], sys.argv[2]
for node in ET.parse(path).iter("node"):
    text = node.get("text") or ""
    desc = node.get("content-desc") or ""
    if needle == "@empty-edit":
        hit = node.get("class") == "android.widget.EditText" and not text.strip()
    else:
        hit = needle in text or needle in desc
    if hit:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        sys.exit(0)
sys.exit(1)
PY
}

wait_for() {
  local needle="$1" timeout="${2:-15}" start
  start=$(date +%s)
  while [ $(($(date +%s) - start)) -lt "$timeout" ]; do
    if dump_ui && find_center "$needle" > /dev/null; then return 0; fi
    sleep 1
  done
  return 1
}

tap() {
  local xy
  xy=$(find_center "$1") || return 1
  # shellcheck disable=SC2086 # "x y" — два аргумента input tap.
  adb shell input tap $xy
}

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
adb shell input keyevent 82 || true # разблокировать экран
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

adb install -r "$apk" || fail "adb install"
adb logcat -c
adb logcat -b crash -c || true

# 1. Холодный старт MainActivity.
start_out=$(adb shell am start -W -n "$pkg/.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER)
echo "$start_out"
echo "$start_out" | grep -q "Status: ok" || fail "am start -W: нет Status: ok"
total=$(echo "$start_out" | sed -n 's/^TotalTime: *//p')
echo "### release-smoke: TotalTime холодного старта ${total:-?} мс (эмулятор, без Baseline Profile из Play)" \
  >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
sleep 3
check_alive "холодный старт"

# 2. Главная → «Новая игра» (navigate(NewGameRoute): сериализатор data object после R8).
wait_for "Новая игра" 20 || fail "Главная не показала «Новая игра»"
tap "Новая игра" || fail "не нажать «Новая игра»"
wait_for "Начать игру" 15 || {
  check_alive "Новая игра"
  fail "экран «Новая игра» не открылся"
}
check_alive "экран «Новая игра»"

# 3. Банкир → «Начать игру» → стол (navigate(TableRoute(gameId)): data class + Room-транзакция).
if tap "@empty-edit"; then
  adb shell input text "Smoke"
  if adb shell dumpsys input_method | grep -q "mInputShown=true"; then adb shell input keyevent 4; fi
fi
sleep 1
if dump_ui && tap "Начать игру" && wait_for "Игроки" 15; then
  check_alive "стол"
else
  check_alive "Начать игру"
  echo "::warning::release-smoke: стол не открылся — проверьте ui.xml/screen.png (процесс жив, это не падение)"
fi

collect
echo "release-smoke: ok"
