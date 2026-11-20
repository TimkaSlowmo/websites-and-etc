# Проём (проект «Батя Макса») — iOS + Cloudflare Workers AI (v0.1)

## 1. ИИ-сервер (5 минут)
```
cd worker
npm i
npx wrangler login
npx wrangler secret put APP_TOKEN      # придумай длинный токен
npx wrangler deploy                    # выдаст https://batya-maksa-ai.<аккаунт>.workers.dev
```
Первый вызов Llama Vision сам примет лицензию Meta.

## 2. iOS (нужен Mac + Xcode 15+)
```
brew install xcodegen
cd ios && xcodegen && open OknoCalc.xcodeproj
```
Xcode → Signing & Capabilities → выбери свою Team → Run на iPhone (камера работает только на устройстве).
В приложении: ⚙︎ → адрес Worker + токен.

## 3. Каталог
⚙︎ → «Загрузить каталог (JSON)». Формат — `ios/OknoCalc/Resources/catalog.json` (цены там примерные).

## v0.2
PDF коммерческого предложения (⚙︎ → название компании), история расчётов (сохранение, повторное открытие, удаление долгим нажатием).

## Установка на iPhone (без Xcode, как NutriMate)
Скачать: [Proyom.ipa](https://github.com/TimkaSlowmo/websites-and-etc/releases/latest/download/Proyom.ipa) (пересобирается после каждого изменения).

Mac/ПК: [Sideloadly](https://sideloadly.io) → iPhone кабелем → перетащить `Proyom.ipa` → Apple ID → Start.
iPhone: Настройки → Основные → VPN и управление устройством → твой Apple ID → Доверять (и Режим разработчика, если попросит).
Бесплатный Apple ID: 7 дней, потом поставить заново поверх (данные сохраняются).
