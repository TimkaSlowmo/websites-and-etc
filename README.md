# websites-and-etc

## 📱 Проём (iPhone) — скачать
**[⬇️ Proyom.ipa](https://github.com/TimkaSlowmo/websites-and-etc/releases/latest/download/Proyom.ipa)** · [страница установки](https://timkaslowmo.github.io/websites-and-etc/proyom/) · исходники в `batya-maksa/`

Установка: Sideloadly на Mac/ПК → iPhone кабелем → перетащить `.ipa` → Apple ID → Start.

Мои сайты и веб-проекты (каждый проект — в своей папке).
NutriMate и другие приложения живут в своих репозиториях.

| Папка | Что это |
|---|---|
| `zhanar-cosma/` | Сайт студии перманентного макияжа Zhanar Cosma (Алматы). Собранная версия из bolt.new, исходников пока нет |
| `batya-maksa/` | **Проём** — iOS-приложение для оконной компании: фото чертежа → ИИ (Cloudflare) читает размеры, рисует готовое окно, считает цену по каталогу |
| `alatau-wear/` | Демо-магазин одежды для портфолио: каталог, фильтры, корзина, заказ в WhatsApp (один HTML-файл) |

## Правила
- Один проект — одна папка в корне.
- В каждой папке свой `README.md` с описанием и способом запуска.

## Ссылки (GitHub Pages)
- https://timkaslowmo.github.io/websites-and-etc/zhanar-cosma/
- https://timkaslowmo.github.io/websites-and-etc/alatau-wear/
- https://timkaslowmo.github.io/websites-and-etc/proyom/ (скачать Проём)

После `git push` в main сайты обновляются сами. Новую папку-сайт добавь в список `for d in ...` и в ссылки на главной в `.github/workflows/pages.yml` (папки приложений вроде `batya-maksa` не публикуются).
