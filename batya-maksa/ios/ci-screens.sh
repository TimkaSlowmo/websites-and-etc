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
ls -la screens
