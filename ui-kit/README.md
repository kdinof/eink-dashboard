# Ink UI

Монохромный веб-UI-кит в стиле e-ink «чернильных карточек»: белая бумага, чёрные чернила, толстые рамки,
чёрные плашки-заголовки, инверсия вместо цвета. Чистые CSS и vanilla JS, без сборки и зависимостей.
Анимации по умолчанию выключены (`--motion: 0ms`), чтобы e-ink экран не оставлял шлейфов.

Визуальный референс лежит в `docs/reference/` (кадры из исходного видео).

## Быстрый старт

```html
<link rel="stylesheet" href="ui-kit/ink.css">
<script src="ui-kit/ink.js" defer></script>

<header class="ink-page-header">
  <div>
    <div class="ink-page-header__eyebrow">INK CARD LIBRARY</div>
    <h1 class="ink-page-header__title">Избранные карты</h1>
  </div>
  <button class="ink-button ink-button--sm ink-page-header__action">
    <svg class="ink-icon"><use href="ui-kit/icons.svg#i-plus"/></svg>Настроить
  </button>
</header>

<article class="ink-card">
  <header class="ink-card__header">Новости дня<span class="ink-card__meta">07.10 пт</span></header>
  <div class="ink-card__body">…</div>
</article>
```

Документация с живыми примерами всех компонентов:

```sh
cd ui-kit && python3 -m http.server 8080   # open http://localhost:8080
```

HTTP нужен для спрайта иконок (`<use href="icons.svg#…">`) и для загрузки фрагментов документации.
Если нужен один файл, который открывается без сервера:

```sh
node ui-kit/scripts/build.mjs
# dist/ink-ui.html     — документация одним файлом
# dist/ink.bundle.css  — все компоненты одним файлом (без @import)
```

## Структура

| Файл | Что внутри |
|---|---|
| `tokens.css` | Все переменные: цвета, шрифты, шкала кегля, отступы, штрихи, радиусы, размеры, анимация |
| `base.css` | Сброс, типографика (`.ink-h1`, `.ink-eyebrow`, `.ink-num` …), раскладка (`.ink-page`, `.ink-stack`, `.ink-row`, `.ink-grid`), `.ink-icon` |
| `components/button.css` | Кнопки, группы кнопок, FAB |
| `components/chip.css` | Чипы-фильтры, теги, бейджи |
| `components/form.css` | Поля, input, textarea, select, поиск, чекбокс, радио, переключатель, сегменты, степпер, слайдер |
| `components/card.css` | Карточка с плашками сверху и снизу, двойная рамка, плитки галереи |
| `components/list.css` | Списки: линованный, нумерованный, хронология; разделители |
| `components/data.css` | Котировки, метрики, прогресс, ключ–значение, листок календаря, погода |
| `components/feedback.css` | Пустое состояние, цитата, алерт, скелетон, загрузка |
| `components/header.css` | App bar, заголовок страницы, заголовок секции |
| `components/nav.css` | Нижний таб-бар, табы, пагинация, меню |
| `components/overlay.css` | Диалог, bottom sheet, тост, поповер, тултип |
| `icons.svg` | Спрайт линейных иконок (`i-*`) |
| `ink.js` | Чипы, табы, таб-бар, диалоги, тост, степпер, часы (`window.InkUI`) |
| `examples/` | Готовые экраны, собранные из компонентов кита |
| `CONVENTIONS.md` | Правила добавления новых компонентов |

## Темизация

Компоненты берут значения только из токенов, поэтому для новой темы достаточно переопределить переменные:

```css
:root {
  --paper: #f4f1e8;       /* тёплая бумага */
  --stroke-bold: 3px;     /* рамки толще */
  --radius-sm: 0;         /* острые углы */
  --motion: 120ms;        /* включить анимации на LCD */
}
```

Токены можно переопределять и на отдельном контейнере: например, `.my-panel { --ink: #222; }` меняет вид только внутри этой панели.
