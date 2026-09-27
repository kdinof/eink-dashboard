# Ink UI — conventions for contributors

Reference: a monochrome "ink card" mobile app (e-ink aesthetic). Screenshots of the
source video: `docs/reference/*.jpg`.

## Visual language (what the reference shows)

- Pure white paper, pure black ink, greys only for secondary text. No colors, no shadows,
  no gradients. Hierarchy comes from **weight, inversion (black fill + white text) and stroke thickness**.
- **Page header**: tiny uppercase letter-spaced English eyebrow in grey (`INK CARD LIBRARY`,
  `CUSTOMIZATION`, `HARDWARE MANAGEMENT`) above a heavy black title. Primary action sits top-right
  as a small black pill with a `+` icon ("+ Customize") or a small black rounded rect ("⊞ Add device").
- **Filter chips**: a horizontal row of plain-text labels; the active one is a solid black pill with white bold text.
- **Cards**: white, thick black frame (~2.5px), small radius (4–8px). Variants seen:
  - *Header band*: full-width black strip at top with white bold title (e.g. "Handwritten note", "Daily news · Headlines"),
    sometimes a right-aligned meta ("07.10 Fri").
  - *Footer band*: black strip at bottom: left label ("National warning center"), right meta ("Updated 07-10 18:00").
  - *Double frame*: outer thin rounded frame + inner thick frame (card preview inside a gallery tile).
  - *Tile + meta*: gallery item = framed preview, then below it a bold title, a grey one-line description,
    and a small outline tag on the right ("Info", "Finance").
- **Lists**: ruled lines like notebook paper; numbered items use solid black circles with white digits;
  secondary line in grey small text (source · time).
- **Ticker row** (stocks/rates): name bold + small boxed code tag under it, big tabular number right,
  black inverted badge with trend arrow `↗ +1.26%`. A black summary bar at the bottom ("SSE 4,031.51 ▲1.12%").
- **Date badge**: small black rectangle with white text ("7月10日").
- **Empty state**: centered thin line icon, bold short title, grey hint.
- **Bottom tab bar**: icon above tiny label; the active tab is an inverted solid black block, slightly taller.
- **Quote card**: small grey caption on top, centered serif/regular text, tiny right-aligned attribution.
- Motion: none. Everything must look right with zero transitions (e-ink).

## Code rules

- Plain CSS + vanilla JS, no build step, no dependencies. Must work from `file://` except the SVG sprite `<use>`.
- Use **only** variables from `tokens.css` for color, font, spacing, stroke, radius, size. No raw hex values in components.
- Class naming (BEM-ish, `ink-` prefix): block `ink-card`, element `ink-card__header`, modifier `ink-card--double`.
- State via ARIA/native attributes first: `aria-selected="true"`, `aria-pressed="true"`, `aria-current="page"`,
  `[disabled]`, `:checked`, `aria-invalid="true"`. Fallback `.is-active` only where no attribute fits.
- Transitions, if any, use `var(--motion)` so they are zero by default.
- Every component must work at 320px width and must not rely on hover (touch + e-ink).
- Tap targets >= `var(--control-md)` height.
- Text in demos: Russian (the kit's users are Russian-speaking); eyebrows stay in uppercase English, like the reference.
- One CSS file per component group in `components/`, header comment listing the classes and a minimal markup example.
- For each component group also write `docs/snippets/<group>.html` — an HTML fragment (no <html>/<head>) with
  `<section class="doc-section" id="...">` blocks: `<h2 class="doc-title">`, one-line `<p class="doc-lead">`,
  then live examples inside `<div class="doc-demo">` followed by the same markup escaped in `<pre class="doc-code"><code>`.
- Icons: `<svg class="ink-icon"><use href="icons.svg#i-plus"/></svg>` (path relative to the page). Names are listed in `icons.svg`.
