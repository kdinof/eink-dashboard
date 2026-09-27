#!/usr/bin/env node
// Ink UI build: no bundler, just concatenation.
//   dist/ink.bundle.css  — ink.css with every @import inlined (one request, works anywhere)
//   dist/ink-ui.html     — self-contained docs page: CSS, JS, icon sprite and snippets inlined,
//                          opens from file:// and can be shared as a single file.
// Usage: node scripts/build.mjs
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const read = (p) => readFileSync(join(root, p), 'utf8');

function inlineCss(file) {
  const dir = dirname(file);
  return read(file).replace(/@import url\("([^"]+)"\);/g, (_, href) => {
    const target = join(dir, href);
    return existsSync(join(root, target)) ? `/* ${target} */\n${inlineCss(target)}` : '';
  });
}

// Sprite <symbol>s go into a hidden inline <svg>; live <use> refs become fragment refs.
// Escaped code samples (&lt;use …) keep the external path readers should copy.
function inlineIcons(html) {
  return html.replace(/<use href="(?:\.\.\/)*(?:ui-kit\/)?icons\.svg#/g, '<use href="#');
}

mkdirSync(join(root, 'dist'), { recursive: true });

const css = inlineCss('ink.css');
writeFileSync(join(root, 'dist/ink.bundle.css'), css);

let page = read('index.html');
page = page.replace(/<div data-snippet="([^"]+)"><\/div>/g, (_, p) => (existsSync(join(root, p)) ? read(p) : ''));
page = page.replace('<link rel="stylesheet" href="ink.css">', `<style>\n${css}\n</style>`);
page = page.replace('<link rel="stylesheet" href="docs/docs.css">', `<style>\n${read('docs/docs.css')}\n</style>`);
page = page.replace('<script src="ink.js"></script>', `<script>\n${read('ink.js')}\n</script>`);
// Examples link to separate pages; in the single-file build keep them pointing at the source tree.
page = page.replace(/href="examples\//g, 'href="../examples/');
const sprite = read('icons.svg')
  .replace(/<\?xml[^>]*>\s*/, '')
  .replace(/<svg\b[^>]*>/, '<svg xmlns="http://www.w3.org/2000/svg" style="display:none" aria-hidden="true">');
page = page.replace('<body>', `<body>\n${sprite}`);
page = inlineIcons(page);
writeFileSync(join(root, 'dist/ink-ui.html'), page);

console.log(`dist/ink.bundle.css  ${(css.length / 1024).toFixed(1)} KB`);
console.log(`dist/ink-ui.html     ${(page.length / 1024).toFixed(1)} KB`);
