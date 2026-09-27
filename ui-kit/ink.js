/*
 * Ink UI — behaviours. Dependency-free, no animations (e-ink).
 * Auto-initialises on DOMContentLoaded; call InkUI.init(root) after inserting markup.
 *
 *   [data-ink-chips] / [data-ink-segmented]  single-select button group -> ink:change {value}
 *   [data-ink-tabs]                          tablist, buttons[aria-controls] toggle panels[hidden]
 *   [data-ink-tabbar]                        bottom nav, aria-current="page" -> ink:change {value}
 *   [data-ink-open="dialogId"]               opens <dialog id> (showModal); [data-ink-close] closes
 *   input.ink-slider                         keeps --fill in sync with the value
 *   [data-ink-stepper]                       [data-ink-step="-1|1"] buttons + input[type=number]
 *   [data-ink-clock]                         HH:MM, updated on the minute; data-format="date" -> "26 сентября, пт"
 *   InkUI.toast(message, {duration})         single .ink-toast[data-open="true"], default 2500ms
 */
(function () {
  "use strict";

  var FLAG = "inkInit"; // stored as data-ink-init="…" listing initialised behaviours

  function once(el, key) {
    var done = (el.dataset[FLAG] || "").split(" ");
    if (done.indexOf(key) !== -1) return false;
    done.push(key);
    el.dataset[FLAG] = done.join(" ").trim();
    return true;
  }

  function each(root, selector, fn) {
    var list = [];
    if (root.matches && root.matches(selector)) list.push(root);
    Array.prototype.push.apply(list, root.querySelectorAll(selector));
    list.forEach(fn);
  }

  function emit(el, value, item) {
    el.dispatchEvent(new CustomEvent("ink:change", { bubbles: true, detail: { value: value, item: item } }));
  }

  function items(container, selector) {
    return Array.prototype.filter.call(container.querySelectorAll(selector), function (el) {
      return !el.disabled && el.getAttribute("aria-disabled") !== "true";
    });
  }

  /* ---------- Chips / segmented: single select ---------- */

  function initSelect(container) {
    if (!once(container, "select")) return;
    container.addEventListener("click", function (e) {
      var btn = e.target.closest("button, [role=tab], [aria-pressed]");
      if (!btn || !container.contains(btn) || btn.disabled) return;
      var buttons = container.querySelectorAll("button, [role=tab], [aria-pressed]");
      Array.prototype.forEach.call(buttons, function (b) {
        var on = b === btn;
        if (b.getAttribute("role") === "tab") {
          b.setAttribute("aria-selected", String(on));
          b.tabIndex = on ? 0 : -1;
        } else {
          b.setAttribute("aria-pressed", String(on));
        }
      });
      emit(container, btn.dataset.value != null ? btn.dataset.value : btn.textContent.trim(), btn);
    });
  }

  /* ---------- Tabs ---------- */

  function selectTab(list, tab, focus) {
    items(list, "[role=tab], [aria-controls]").forEach(function (t) {
      var on = t === tab;
      t.setAttribute("aria-selected", String(on));
      t.tabIndex = on ? 0 : -1;
      var panel = document.getElementById(t.getAttribute("aria-controls"));
      if (panel) panel.hidden = !on;
    });
    if (focus) tab.focus();
    emit(list, tab.dataset.value != null ? tab.dataset.value : tab.getAttribute("aria-controls"), tab);
  }

  function initTabs(list) {
    if (!once(list, "tabs")) return;
    if (!list.hasAttribute("role")) list.setAttribute("role", "tablist");
    var tabs = items(list, "[aria-controls]");
    var current = tabs.filter(function (t) { return t.getAttribute("aria-selected") === "true"; })[0] || tabs[0];
    tabs.forEach(function (t) {
      if (!t.hasAttribute("role")) t.setAttribute("role", "tab");
      var on = t === current;
      t.setAttribute("aria-selected", String(on));
      t.tabIndex = on ? 0 : -1;
      var panel = document.getElementById(t.getAttribute("aria-controls"));
      if (panel) {
        panel.hidden = !on;
        if (!panel.hasAttribute("role")) panel.setAttribute("role", "tabpanel");
      }
    });

    list.addEventListener("click", function (e) {
      var tab = e.target.closest("[aria-controls]");
      if (tab && list.contains(tab) && !tab.disabled) selectTab(list, tab, false);
    });

    list.addEventListener("keydown", function (e) {
      var all = items(list, "[aria-controls]");
      var i = all.indexOf(document.activeElement);
      if (i === -1) return;
      var next = null;
      switch (e.key) {
        case "ArrowRight": case "ArrowDown": next = all[(i + 1) % all.length]; break;
        case "ArrowLeft": case "ArrowUp": next = all[(i - 1 + all.length) % all.length]; break;
        case "Home": next = all[0]; break;
        case "End": next = all[all.length - 1]; break;
        default: return;
      }
      e.preventDefault();
      selectTab(list, next, true);
    });
  }

  /* ---------- Tab bar ---------- */

  function initTabbar(bar) {
    if (!once(bar, "tabbar")) return;
    bar.addEventListener("click", function (e) {
      var item = e.target.closest("a, button");
      if (!item || !bar.contains(item) || item.disabled) return;
      Array.prototype.forEach.call(bar.querySelectorAll("a, button"), function (el) {
        if (el === item) el.setAttribute("aria-current", "page");
        else el.removeAttribute("aria-current");
      });
      var value = item.dataset.value != null ? item.dataset.value : (item.getAttribute("href") || item.textContent.trim());
      emit(bar, value, item);
    });
  }

  /* ---------- Dialog ---------- */

  function closeDialog(dialog, value) {
    if (dialog && dialog.open) dialog.close(value || "");
  }

  function initDialog(dialog) {
    if (!once(dialog, "dialog")) return;
    dialog.addEventListener("click", function (e) {
      var closer = e.target.closest("[data-ink-close]");
      if (closer && dialog.contains(closer)) {
        closeDialog(dialog, closer.getAttribute("data-ink-close") || closer.value);
        return;
      }
      // Click on the backdrop: the event target is the <dialog> itself, outside its content box.
      if (e.target === dialog) {
        var r = dialog.getBoundingClientRect();
        var inside = e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom;
        if (!inside) closeDialog(dialog);
      }
    });
  }

  function openDialog(id) {
    var dialog = typeof id === "string" ? document.getElementById(id) : id;
    if (!dialog || dialog.open) return dialog;
    initDialog(dialog);
    if (typeof dialog.showModal === "function") dialog.showModal();
    else dialog.setAttribute("open", "");
    return dialog;
  }

  function initOpener(btn) {
    if (!once(btn, "open")) return;
    btn.addEventListener("click", function (e) {
      e.preventDefault();
      openDialog(btn.getAttribute("data-ink-open"));
    });
  }

  /* ---------- Toast ---------- */

  var toastEl = null;
  var toastTimer = 0;

  function toast(message, opts) {
    var duration = opts && opts.duration != null ? opts.duration : 2500;
    if (!toastEl || !toastEl.isConnected) {
      toastEl = document.querySelector("body > .ink-toast");
      if (!toastEl) {
        toastEl = document.createElement("div");
        toastEl.className = "ink-toast";
        toastEl.setAttribute("role", "status");
        toastEl.setAttribute("aria-live", "polite");
        document.body.appendChild(toastEl);
      }
    }
    toastEl.textContent = message;
    toastEl.setAttribute("data-open", "true");
    clearTimeout(toastTimer);
    if (duration > 0) {
      toastTimer = setTimeout(function () { toastEl.setAttribute("data-open", "false"); }, duration);
    }
    return toastEl;
  }

  /* ---------- Stepper ---------- */

  function decimals(n) {
    var s = String(n);
    var i = s.indexOf(".");
    return i === -1 ? 0 : s.length - i - 1;
  }

  function initStepper(box) {
    if (!once(box, "stepper")) return;
    var input = box.querySelector("input[type=number]");
    if (!input) return;

    function sync() {
      var v = parseFloat(input.value);
      var min = input.min !== "" ? parseFloat(input.min) : -Infinity;
      var max = input.max !== "" ? parseFloat(input.max) : Infinity;
      Array.prototype.forEach.call(box.querySelectorAll("[data-ink-step]"), function (b) {
        var dir = parseFloat(b.getAttribute("data-ink-step"));
        b.disabled = !isNaN(v) && ((dir < 0 && v <= min) || (dir > 0 && v >= max));
      });
    }

    box.addEventListener("click", function (e) {
      var b = e.target.closest("[data-ink-step]");
      if (!b || !box.contains(b) || b.disabled || input.disabled || input.readOnly) return;
      var dir = parseFloat(b.getAttribute("data-ink-step")) || 0;
      var step = input.step && input.step !== "any" ? parseFloat(input.step) : 1;
      var min = input.min !== "" ? parseFloat(input.min) : -Infinity;
      var max = input.max !== "" ? parseFloat(input.max) : Infinity;
      var v = parseFloat(input.value);
      if (isNaN(v)) v = isFinite(min) ? min : 0;
      var next = Math.min(max, Math.max(min, v + dir * step));
      next = parseFloat(next.toFixed(Math.max(decimals(step), decimals(v))));
      if (next === parseFloat(input.value)) return;
      input.value = String(next);
      input.dispatchEvent(new Event("input", { bubbles: true }));
      input.dispatchEvent(new Event("change", { bubbles: true }));
      emit(box, next, input);
      sync();
    });
    input.addEventListener("input", sync);
    sync();
  }

  /* ---------- Slider ---------- */

  // .ink-slider paints its filled track from --fill; keep it in sync with the value.
  function initSlider(input) {
    if (!once(input, "slider")) return;
    function sync() {
      var min = parseFloat(input.min) || 0;
      var max = input.max !== "" ? parseFloat(input.max) : 100;
      var pct = max > min ? ((parseFloat(input.value) - min) / (max - min)) * 100 : 0;
      input.style.setProperty("--fill", pct + "%");
    }
    input.addEventListener("input", sync);
    sync();
  }

  /* ---------- Clock ---------- */

  var clocks = [];
  var clockTimer = 0;
  var dateFmt = null;
  var weekdayFmt = null;

  function pad(n) { return n < 10 ? "0" + n : String(n); }

  function formatDate(d) {
    try {
      dateFmt = dateFmt || new Intl.DateTimeFormat("ru-RU", { day: "numeric", month: "long" });
      weekdayFmt = weekdayFmt || new Intl.DateTimeFormat("ru-RU", { weekday: "short" });
      return dateFmt.format(d) + ", " + weekdayFmt.format(d).replace(".", "");
    } catch (err) {
      return pad(d.getDate()) + "." + pad(d.getMonth() + 1);
    }
  }

  function renderClock(el, now) {
    var text = el.getAttribute("data-format") === "date"
      ? formatDate(now)
      : pad(now.getHours()) + ":" + pad(now.getMinutes());
    if (el.textContent !== text) el.textContent = text; // avoid needless e-ink redraws
    if (el.tagName === "TIME") el.setAttribute("datetime", now.toISOString());
  }

  function tick() {
    var now = new Date();
    clocks = clocks.filter(function (el) { return el.isConnected; });
    clocks.forEach(function (el) { renderClock(el, now); });
    clearTimeout(clockTimer);
    if (!clocks.length) { clockTimer = 0; return; }
    // Schedule on the next minute boundary (+50ms slack), no per-second updates.
    var ms = 60000 - (now.getSeconds() * 1000 + now.getMilliseconds()) + 50;
    clockTimer = setTimeout(tick, ms);
  }

  function initClock(el) {
    if (!once(el, "clock")) return;
    clocks.push(el);
    renderClock(el, new Date());
    if (!clockTimer) tick();
  }

  // Timers are throttled in background tabs; resync when the page is shown again.
  document.addEventListener("visibilitychange", function () {
    if (!document.hidden && clocks.length) tick();
  });

  /* ---------- Init ---------- */

  function init(root) {
    root = root || document;
    each(root, "[data-ink-chips], [data-ink-segmented]", initSelect);
    each(root, "[data-ink-tabs]", initTabs);
    each(root, "[data-ink-tabbar]", initTabbar);
    each(root, "[data-ink-open]", initOpener);
    each(root, "dialog", function (d) {
      if (d.querySelector("[data-ink-close]") || d.id) initDialog(d);
    });
    each(root, "[data-ink-stepper]", initStepper);
    each(root, "input.ink-slider", initSlider);
    each(root, "[data-ink-clock]", initClock);
    return root;
  }

  window.InkUI = {
    init: init,
    toast: toast,
    open: openDialog,
    close: function (id) { closeDialog(typeof id === "string" ? document.getElementById(id) : id); }
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () { init(document); });
  } else {
    init(document);
  }
})();
