#!/usr/bin/env bash
# Скриншоты iOS-приложения на симуляторе: демо-расчёт (схема), прокрутка, вкладки.
set -u
SIM="$1"
APP=$(find sim/Build/Products/Debug-iphonesimulator -maxdepth 1 -name "*.app" | head -1)
mkdir -p screens
UDID=$(xcrun simctl list devices available | grep -m1 "$SIM (" | grep -oE '[0-9A-F-]{36}')
xcrun simctl boot "$UDID" || true
xcrun simctl bootstatus "$UDID" -b
xcrun simctl install "$UDID" "$APP"
BID=kz.okno.calc
shot() { sleep "$2"; xcrun simctl io "$UDID" screenshot "screens/$1.png"; }
# КП демо в PDF (для проверки макета)
xcrun simctl launch "$UDID" $BID -demo -pdf; sleep 8
DATA=$(xcrun simctl get_app_container "$UDID" $BID data 2>/dev/null)
[ -n "$DATA" ] && cp "$DATA/Documents/demo-kp.pdf" screens/demo-kp.pdf 2>/dev/null || echo "PDF не найден"
xcrun simctl terminate "$UDID" $BID

# Светлая и тёмная тема (тема приложения «Как в системе»)
for mode in light dark; do
  xcrun simctl ui "$UDID" appearance $mode
  xcrun simctl launch "$UDID" $BID -demo; shot "$mode-1-result" 6
  xcrun simctl terminate "$UDID" $BID
  xcrun simctl launch "$UDID" $BID; shot "$mode-2-home" 4
  for t in history catalog settings; do
    xcrun simctl terminate "$UDID" $BID
    xcrun simctl launch "$UDID" $BID -tab $t; shot "$mode-3-$t" 4
  done
  xcrun simctl terminate "$UDID" $BID
done

# Черновик КП: окно, добавленное вручную, должно открыться после перезапуска (без аргументов)
xcrun simctl ui "$UDID" appearance light
xcrun simctl launch "$UDID" $BID -manual; sleep 5
xcrun simctl terminate "$UDID" $BID
DATA=$(xcrun simctl get_app_container "$UDID" $BID data 2>/dev/null)
if [ -n "$DATA" ] && grep -q "ОК-1" "$DATA/Documents/draft/draft.json" 2>/dev/null; then echo "Черновик КП сохранён"
else echo "Черновик КП не сохранился"; exit 1; fi
xcrun simctl launch "$UDID" $BID; shot "light-4-draft-restored" 5
xcrun simctl terminate "$UDID" $BID
ls -la screens
