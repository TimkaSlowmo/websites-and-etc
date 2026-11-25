#!/usr/bin/env bash
# Смоук-тест APK на эмуляторе: запуск, демо-расчёт, редактор ячейки, цифровая клавиатура и её скрытие,
# КП (PDF), вкладки — в светлой и тёмной теме. Падение или неубираемая клавиатура = ошибка.
set -u
mkdir -p screens
adb install -r Proyom.apk
adb logcat -c
W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
H=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f2)
FAIL=0
start() { adb shell am force-stop kz.okno.proyom; adb shell am start -n kz.okno.proyom/.MainActivity "$@" >/dev/null; }
shot() { sleep "$2"; adb exec-out screencap -p > "screens/$1.png"; }
swipe() { adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 350; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | tr '>' '\n'; }
# Нажать на элемент с текстом (совпадение начала), только если он целиком в видимой части
# (между заголовком и панелью вкладок); иначе прокрутить и искать снова
tap_text() {
  local t=$1 x1 y1 x2 y2
  for i in 1 2 3 4 5 6 7; do
    dump | grep "text=\"$t" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | tr -c '0-9\n' ' ' > /tmp/bounds.txt
    while read -r x1 y1 x2 y2; do
      if [ -n "${y2:-}" ] && [ "$y1" -gt $((H*10/100)) ] && [ "$y2" -lt $((H*86/100)) ]; then
        adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )); return 0
      fi
    done < /tmp/bounds.txt
    swipe; sleep 1
  done
  return 1
}
keyboard() { adb shell dumpsys input_method | grep -q "mInputShown=true"; }

run() {
  local m=$1
  start; shot "$m-1-home" 6
  start --ez demo true; shot "$m-2-result" 5
  # нажатие по верхней левой ячейке схемы — откроется редактор ячейки
  adb shell input tap $((W*38/100)) $((H*36/100)); shot "$m-3-cell-editor" 3
  adb shell input keyevent KEYCODE_BACK; sleep 2
  for i in 4 5 6 7; do swipe; shot "$m-$i-result-scroll" 2; done
  if [ "$m" = light ]; then
    # Цифровое поле: клавиатура только с цифрами, ввод 1500, «Готово» прячет клавиатуру и применяет размер
    start --ez demo true; sleep 4
    if tap_text "1200"; then
      sleep 2; shot "$m-8-keypad" 1
      if keyboard; then KB=1; else KB=0; echo "WARN: экранная клавиатура не показалась (аппаратная клавиатура эмулятора?)"; fi
      adb shell input keyevent KEYCODE_MOVE_END
      for i in 1 2 3 4 5; do adb shell input keyevent KEYCODE_DEL; done
      adb shell input text 1500; sleep 1; shot "$m-8b-typed" 0
      # «Готово» (✓) на цифровой клавиатуре — правый нижний угол. Аппаратный ENTER не годится:
      # он выводит окно из сенсорного режима, и отпускание клавиши «нажимает» первую кнопку экрана.
      if [ $KB = 1 ]; then
        adb shell input tap $((W*745/1000)) $((H*955/1000)); sleep 2
        keyboard && { echo "WARN: ✓ не попал — прячу тапом мимо поля"; tap_text "Площадь"; sleep 2; } || echo "INFO: «Готово» закрыл клавиатуру"
      else tap_text "Площадь"; sleep 2; fi
      [ $KB = 1 ] && keyboard && { echo "Клавиатура не скрылась по тапу мимо поля"; FAIL=1; }
      dump | grep -q 'text="1500"' || { echo "Ширина 1500 не применилась"; FAIL=1; }
      shot "$m-9-after-done" 1
      # Повторно: фокус в поле и тап мимо — клавиатура должна уйти
      tap_text "1500" && sleep 2 && tap_text "Площадь" && sleep 2
      [ $KB = 1 ] && keyboard && { echo "Клавиатура не скрылась по тапу мимо поля (2)"; FAIL=1; }
    else echo "Поле ширины не найдено"; FAIL=1; fi
    # КП (PDF): должно открыться окно «Поделиться», без падения
    start --ez demo true; sleep 4
    tap_text "Создать и отправить КП" || { echo "Кнопка КП не найдена"; FAIL=1; }
    shot "$m-10-pdf-share" 4
    adb shell input keyevent KEYCODE_BACK; sleep 1
  fi
  for t in history catalog settings; do start --es tab $t; shot "$m-11-$t" 4; done
}

adb shell cmd uimode night no; run light
adb shell cmd uimode night yes; sleep 2; run dark
adb shell cmd uimode night no

adb logcat -d > screens/logcat.txt
if grep -q "FATAL EXCEPTION" screens/logcat.txt; then
  grep -A30 "FATAL EXCEPTION" screens/logcat.txt
  echo "Приложение упало"; exit 1
fi
adb shell pidof kz.okno.proyom || { echo "Процесс не запущен"; exit 1; }
[ $FAIL = 0 ] || exit 1
echo "Смоук-тест пройден"
