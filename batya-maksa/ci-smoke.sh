#!/usr/bin/env bash
# Смоук-тест APK на эмуляторе: установка, запуск, переходы по вкладкам, скриншоты, проверка падений.
set -u
mkdir -p screens
adb install -r Proyom.apk
adb logcat -c
adb shell am start -n kz.okno.proyom/.MainActivity
sleep 8
adb exec-out screencap -p > screens/1-home.png
W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
H=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f2)
Y=$((H - H * 45 / 1000))
i=2
for n in 3 5 7; do
  adb shell input tap $((W * n / 8)) $Y; sleep 3
  adb exec-out screencap -p > screens/$i-tab.png; i=$((i+1))
done
adb shell input tap $((W / 8)) $Y; sleep 2
# ручной ввод → экран расчёта
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
B=$(adb shell cat /sdcard/ui.xml | grep -oE 'text="Ввести параметры вручную"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]')
if [ -n "$B" ]; then
  X1=$(echo $B | grep -oE '[0-9]+' | sed -n 1p); Y1=$(echo $B | grep -oE '[0-9]+' | sed -n 2p)
  X2=$(echo $B | grep -oE '[0-9]+' | sed -n 3p); Y2=$(echo $B | grep -oE '[0-9]+' | sed -n 4p)
  adb shell input tap $(((X1 + X2) / 2)) $(((Y1 + Y2) / 2)); sleep 4
  adb exec-out screencap -p > screens/5-result.png
  adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 400; sleep 2
  adb exec-out screencap -p > screens/6-result-scroll.png
  adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 400; sleep 2
  adb exec-out screencap -p > screens/7-price.png
fi
adb logcat -d > screens/logcat.txt
if grep -q "FATAL EXCEPTION" screens/logcat.txt; then
  grep -A30 "FATAL EXCEPTION" screens/logcat.txt
  echo "Приложение упало"; exit 1
fi
adb shell pidof kz.okno.proyom || { echo "Процесс не запущен"; exit 1; }
echo "Смоук-тест пройден"
