# Батя Макса — iOS + Cloudflare Workers AI (v0.1)

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
