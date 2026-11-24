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
xcrun simctl launch "$UDID" $BID -demo; shot 1-result-scheme 6
xcrun simctl terminate "$UDID" $BID
xcrun simctl launch "$UDID" $BID; shot 2-home 4
for t in history catalog settings; do
  xcrun simctl terminate "$UDID" $BID
  xcrun simctl launch "$UDID" $BID -tab $t; shot "3-$t" 4
done
xcrun simctl terminate "$UDID" $BID
ls -la screens
