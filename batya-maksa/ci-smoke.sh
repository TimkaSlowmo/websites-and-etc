#!/usr/bin/env bash
# Смоук-тест APK на эмуляторе: установка, запуск, демо-расчёт (схема, прокрутка), вкладки, проверка падений.
set -u
mkdir -p screens
adb install -r Proyom.apk
adb logcat -c
W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
H=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f2)
start() { adb shell am force-stop kz.okno.proyom; adb shell am start -n kz.okno.proyom/.MainActivity "$@" >/dev/null; }
shot() { sleep "$2"; adb exec-out screencap -p > "screens/$1.png"; }
swipe() { adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 350; }

start; shot 1-home 8
start --ez demo true; shot 2-result-scheme 6
# нажатие по верхней левой ячейке схемы — откроется редактор ячейки
adb shell input tap $((W*38/100)) $((H*36/100)); shot 3-cell-editor 3
adb shell input keyevent KEYCODE_BACK; sleep 2
for i in 4 5 6 7 8; do swipe; shot "$i-result-scroll" 2; done
for t in history catalog settings; do start --es tab $t; shot "9-$t" 5; done
adb logcat -d > screens/logcat.txt
if grep -q "FATAL EXCEPTION" screens/logcat.txt; then
  grep -A30 "FATAL EXCEPTION" screens/logcat.txt
  echo "Приложение упало"; exit 1
fi
adb shell pidof kz.okno.proyom || { echo "Процесс не запущен"; exit 1; }
echo "Смоук-тест пройден"
