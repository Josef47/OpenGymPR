/* OpenGymPR — log lifts, see PRs. No ads, no trackers, no account, no server.
   All data lives in this browser's localStorage. Weights are stored in kg; lb is display only. */
"use strict";

const KEY = "opengympr.v1";
const LB = 0.45359237;
const DEFAULT_EXERCISES = [
  "Bench Press", "Squat", "Deadlift", "Overhead Press", "Barbell Row", "Pull-up", "Incline Bench Press",
  "Romanian Deadlift", "Front Squat", "Dumbbell Bench Press", "Dumbbell Shoulder Press", "Lat Pulldown",
  "Leg Press", "Hip Thrust", "Dips", "Bicep Curl", "Tricep Pushdown", "Leg Curl", "Leg Extension", "Calf Raise",
];

// ---------- storage ----------
function fresh() {
  return { v: 1, unit: "kg", theme: "auto", last: null,
    exercises: DEFAULT_EXERCISES.map((name, i) => ({ id: "d" + i, name })), sets: [] };
}
function load() {
  try {
    const s = JSON.parse(localStorage.getItem(KEY));
    if (s && s.v === 1 && Array.isArray(s.sets) && Array.isArray(s.exercises)) return s;
  } catch {}
  return fresh();
}
let S = load();
function save() {
  try { localStorage.setItem(KEY, JSON.stringify(S)); }
  catch { toast("Could not save — storage full or blocked. Export a backup in Settings."); }
}
// Ask the browser not to evict our data under storage pressure (best effort).
if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});

// ---------- helpers ----------
const $ = (s, el = document) => el.querySelector(s);
const esc = (t) => String(t).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
const toUnit = (kg) => (S.unit === "lb" ? kg / LB : kg);
const fromUnit = (v) => (S.unit === "lb" ? v * LB : v);
const fmt = (kg) => { const v = toUnit(kg); return (Math.round(v * 10) / 10).toString().replace(/\.0$/, ""); };
const step = () => (S.unit === "lb" ? 5 : 2.5);
const e1rm = (w, r) => (r <= 1 ? w : w * (1 + r / 30));          // Epley
const dayKey = (t) => { const d = new Date(t); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`; };
const dateLabel = (t) => new Date(t).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "2-digit" });
const exName = (id) => (S.exercises.find((e) => e.id === id) || { name: "?" }).name;
const setsOf = (id) => S.sets.filter((s) => s.e === id).sort((a, b) => a.t - b.t);

function toast(html, { pr = false, action, ms = 2600 } = {}) {
  const t = $("#toast");
  t.className = "toast show" + (pr ? " pr" : "");
  t.innerHTML = `<span>${html}</span>` + (action ? `<button type="button">${action.label}</button>` : "");
  if (action) $("button", t).onclick = () => { action.run(); t.className = "toast"; };
  clearTimeout(toast.h);
  toast.h = setTimeout(() => (t.className = "toast"), ms);
}

// ---------- PR logic ----------
// Which records does `set` break, compared with everything logged before it?
function prsOf(set, prior) {
  if (!prior.length) return [];
  const out = [];
  if (set.w > Math.max(...prior.map((p) => p.w))) out.push("Weight PR");
  if (set.r <= 12 && e1rm(set.w, set.r) > Math.max(...prior.filter((p) => p.r <= 12).map((p) => e1rm(p.w, p.r)), 0) && !out.length) out.push("e1RM PR");
  const sameOrHeavier = prior.filter((p) => p.w >= set.w - 1e-9);
  if (!out.length && sameOrHeavier.length && set.r > Math.max(...sameOrHeavier.map((p) => p.r))) out.push(`Rep PR @ ${fmt(set.w)}`);
  return out;
}
// PR flags for a whole history, computed chronologically (not stored, so deleting a set fixes itself).
function flagged(id) {
  const list = setsOf(id);
  return list.map((s, i) => ({ ...s, prs: prsOf(s, list.slice(0, i)) }));
}
function bestE1rm(list) { return list.reduce((m, s) => (s.r <= 12 ? Math.max(m, e1rm(s.w, s.r)) : m), 0); }
function repMaxes(list) {
  const rm = {};
  for (const s of list) for (let r = 1; r <= Math.min(s.r, 12); r++) if (!rm[r] || s.w > rm[r].w) rm[r] = s;
  return rm;
}

// ---------- views ----------
const view = $("#view");
let current = null; // exercise id selected on the Log screen

function renderLog() {
  if (!current || !S.exercises.some((e) => e.id === current)) current = S.last || S.exercises[0].id;
  const hist = setsOf(current);
  const prev = hist[hist.length - 1];
  const w = prev ? toUnit(prev.w) : S.unit === "lb" ? 45 : 20;
  const r = prev ? prev.r : 5;
  const best = bestE1rm(hist);

  const today = S.sets.filter((s) => dayKey(s.t) === dayKey(Date.now())).sort((a, b) => a.t - b.t);
  const byEx = {};
  for (const s of today) (byEx[s.e] ||= []).push(s);
  const flagsById = {};
  for (const id of Object.keys(byEx)) for (const s of flagged(id)) flagsById[s.id] = s.prs;

  view.innerHTML = `
    <section class="card" aria-label="Log a set">
      <button class="pick" id="pick" type="button"><span>${esc(exName(current))}</span><span class="chev">change ▾</span></button>
      <p class="hint">${prev ? `Last: <b>${fmt(prev.w)} ${S.unit} × ${prev.r}</b> · ${dateLabel(prev.t)}` : "First time — nice."}
        ${best ? ` · Best e1RM <b>${fmt(best)}</b>` : ""}</p>
      <div class="steppers">
        <div class="stepper"><label for="w">Weight (${S.unit})</label>
          <div class="row"><button type="button" data-d="w:-1" aria-label="Less weight">−</button>
          <input id="w" type="number" inputmode="decimal" step="any" min="0" value="${Math.round(w * 10) / 10}">
          <button type="button" data-d="w:1" aria-label="More weight">+</button></div></div>
        <div class="stepper"><label for="r">Reps</label>
          <div class="row"><button type="button" data-d="r:-1" aria-label="Fewer reps">−</button>
          <input id="r" type="number" inputmode="numeric" step="1" min="1" value="${r}">
          <button type="button" data-d="r:1" aria-label="More reps">+</button></div></div>
      </div>
      <button class="btn" id="logBtn" type="button">Log set</button>
    </section>

    <h2>Today</h2>
    ${today.length ? Object.entries(byEx).map(([id, list]) => `
      <div class="group">
        <div class="group-head"><a href="#/ex/${id}">${esc(exName(id))}</a><span class="muted small">${list.length} set${list.length > 1 ? "s" : ""}</span></div>
        <ul class="sets">${list.map((s, i) => `
          <li><span class="idx">${i + 1}</span><span class="val">${fmt(s.w)} ${S.unit} × ${s.r}</span>
            <span>${(flagsById[s.id] || []).map((p) => `<span class="badge">${esc(p)}</span>`).join(" ")}</span>
            <button class="del" type="button" data-del="${s.id}" aria-label="Delete set">×</button></li>`).join("")}
        </ul></div>`).join("") : `<div class="empty">No sets yet today.<br>Pick an exercise, set weight and reps, hit <b>Log set</b>.</div>`}`;

  $("#pick").onclick = openPicker;
  view.querySelectorAll("[data-d]").forEach((b) => (b.onclick = () => {
    const [f, d] = b.dataset.d.split(":");
    const el = $("#" + f);
    const v = parseFloat(el.value) || 0;
    el.value = f === "w" ? Math.max(0, Math.round((v + d * step()) * 100) / 100) : Math.max(1, v + +d);
  }));
  $("#logBtn").onclick = logSet;
  view.querySelectorAll("[data-del]").forEach((b) => (b.onclick = () => deleteSet(b.dataset.del)));
}

function logSet() {
  const w = parseFloat($("#w").value);
  const r = parseInt($("#r").value, 10);
  if (!(w >= 0) || !(r >= 1) || r > 100 || w > 2000) return toast("Check weight and reps.");
  const set = { id: uid(), e: current, w: Math.round(fromUnit(w) * 1000) / 1000, r, t: Date.now() };
  const prs = prsOf(set, setsOf(current));
  S.sets.push(set);
  S.last = current;
  save();
  renderLog();
  if (navigator.vibrate) navigator.vibrate(prs.length ? [30, 60, 30] : 15);
  prs.length
    ? toast(`🏆 ${esc(prs[0])}! ${fmt(set.w)} × ${r}`, { pr: true, ms: 3500 })
    : toast(`Logged ${fmt(set.w)} ${S.unit} × ${r}`, { action: { label: "Undo", run: () => deleteSet(set.id, true) } });
}

function deleteSet(id, silent) {
  const i = S.sets.findIndex((s) => s.id === id);
  if (i < 0) return;
  const [gone] = S.sets.splice(i, 1);
  save();
  route();
  if (!silent) toast("Set deleted", { action: { label: "Undo", run: () => { S.sets.push(gone); save(); route(); } } });
}

// exercise picker (bottom sheet)
function openPicker() {
  const dlg = $("#sheet");
  const counts = {};
  for (const s of S.sets) counts[s.e] = (counts[s.e] || 0) + 1;
  const draw = (q = "") => {
    const term = q.trim().toLowerCase();
    const list = S.exercises.filter((e) => e.name.toLowerCase().includes(term))
      .sort((a, b) => (counts[b.id] || 0) - (counts[a.id] || 0) || a.name.localeCompare(b.name));
    const exact = S.exercises.some((e) => e.name.toLowerCase() === term);
    $("ul", dlg).innerHTML = (term && !exact ? `<li><button type="button" class="add" data-add>+ Add “${esc(q.trim())}”</button></li>` : "") +
      list.map((e) => `<li><button type="button" data-id="${e.id}"><span>${esc(e.name)}</span><span class="muted">${counts[e.id] ? counts[e.id] + " sets" : ""}</span></button></li>`).join("");
    dlg.querySelectorAll("[data-id]").forEach((b) => (b.onclick = () => choose(b.dataset.id)));
    const add = $("[data-add]", dlg);
    if (add) add.onclick = () => {
      const name = q.trim().slice(0, 60);
      const ex = { id: "c" + uid(), name, custom: true };
      S.exercises.push(ex); save(); choose(ex.id);
    };
  };
  const choose = (id) => { current = id; S.last = id; save(); dlg.close(); renderLog(); };
  dlg.innerHTML = `<div class="sheet-head"><input type="search" placeholder="Search or add exercise…" aria-label="Search exercise"></div><ul></ul>`;
  const input = $("input", dlg);
  input.oninput = () => draw(input.value);
  input.onkeydown = (e) => { if (e.key === "Enter") { const first = $("ul button", dlg); if (first) first.click(); } };
  dlg.onclick = (e) => { if (e.target === dlg) dlg.close(); };
  draw();
  dlg.showModal();
}

function sparkPath(vals, w = 96, h = 36) {
  if (vals.length < 2) return "";
  const min = Math.min(...vals), max = Math.max(...vals), span = max - min || 1;
  return "M" + vals.map((v, i) => `${(i / (vals.length - 1)) * (w - 4) + 2},${h - 3 - ((v - min) / span) * (h - 6)}`).join("L");
}
// best e1RM per training day
function dailyBest(list) {
  const days = new Map();
  for (const s of list) {
    if (s.r > 12) continue;
    const k = dayKey(s.t), v = e1rm(s.w, s.r);
    if (!days.has(k) || v > days.get(k).v) days.set(k, { t: s.t, v });
  }
  return [...days.values()].sort((a, b) => a.t - b.t);
}

function renderProgress() {
  const used = S.exercises.map((e) => ({ e, list: setsOf(e.id) })).filter((x) => x.list.length)
    .sort((a, b) => b.list[b.list.length - 1].t - a.list[a.list.length - 1].t);
  view.innerHTML = `<h2>Progress</h2>` + (used.length ? `<ul class="ex-list">${used.map(({ e, list }) => {
    const best = bestE1rm(list);
    const vals = dailyBest(list).map((d) => d.v);
    return `<li><a href="#/ex/${e.id}"><div><div class="name">${esc(e.name)}</div>
      <div class="sub num">${best ? `e1RM ${fmt(best)} ${S.unit}` : `${list.length} sets`} · ${dateLabel(list[list.length - 1].t)}</div></div>
      <svg class="spark" viewBox="0 0 96 36" aria-hidden="true"><path d="${sparkPath(vals)}"/></svg></a></li>`;
  }).join("")}</ul>` : `<div class="empty">Nothing logged yet.<br>Your PR charts show up here.</div>`);
}

let range = "all";
function chartSVG(points) {
  const W = 340, H = 190, L = 34, R = 10, T = 12, B = 22;
  if (points.length < 2) return `<p class="empty">Log this lift on two different days to see a chart.</p>`;
  const vals = points.map((p) => toUnit(p.v));
  let min = Math.min(...vals), max = Math.max(...vals);
  const pad = (max - min) * 0.15 || 2.5; min -= pad; max += pad;
  const t0 = points[0].t, t1 = points[points.length - 1].t, tspan = t1 - t0 || 1;
  const x = (t) => L + ((t - t0) / tspan) * (W - L - R);
  const y = (v) => T + (1 - (v - min) / (max - min)) * (H - T - B);
  const ticks = [0, 1, 2, 3].map((i) => min + ((max - min) * i) / 3);
  let runMax = 0;
  const pts = points.map((p, i) => { const pr = vals[i] > runMax; runMax = Math.max(runMax, vals[i]); return { X: x(p.t), Y: y(vals[i]), pr }; });
  const line = "M" + pts.map((p) => `${p.X.toFixed(1)},${p.Y.toFixed(1)}`).join("L");
  return `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Estimated one-rep max over time">
    <g class="grid">${ticks.map((v) => `<line x1="${L}" x2="${W - R}" y1="${y(v)}" y2="${y(v)}"/><text x="${L - 6}" y="${y(v) + 3}" text-anchor="end">${Math.round(v)}</text>`).join("")}</g>
    <path class="area" d="${line}L${pts[pts.length - 1].X},${H - B}L${pts[0].X},${H - B}Z"/>
    <path class="line" d="${line}"/>
    ${pts.length <= 40 ? pts.map((p) => `<circle class="${p.pr ? "pr" : ""}" cx="${p.X}" cy="${p.Y}" r="3.5"/>`).join("") : ""}
    <text class="xl" x="${L}" y="${H - 5}">${dateLabel(t0)}</text>
    <text class="xl" x="${W - R}" y="${H - 5}" text-anchor="end">${dateLabel(t1)}</text></svg>`;
}

function renderDetail(id) {
  const ex = S.exercises.find((e) => e.id === id);
  if (!ex) return (location.hash = "#/progress");
  const list = flagged(id);
  const since = { "3m": 92, "1y": 366, all: Infinity }[range] * 864e5;
  const points = dailyBest(list).filter((p) => Date.now() - p.t <= since);
  const rm = repMaxes(list);
  const heaviest = list.reduce((m, s) => Math.max(m, s.w), 0);
  const recent = [...list].reverse().slice(0, 60);
  view.innerHTML = `
    <a class="back" href="#/progress">‹ Progress</a>
    <h1 class="detail-title">${esc(ex.name)}</h1>
    <div class="kpis">
      <div class="kpi"><b>${fmt(bestE1rm(list))}</b><span>best e1RM</span></div>
      <div class="kpi"><b>${fmt(heaviest)}</b><span>heaviest (${S.unit})</span></div>
      <div class="kpi"><b>${list.length}</b><span>sets</span></div>
    </div>
    <div class="seg" role="group" aria-label="Chart range">${[["3m", "3 months"], ["1y", "1 year"], ["all", "All"]].map(([k, l]) =>
      `<button type="button" data-range="${k}" aria-pressed="${range === k}">${l}</button>`).join("")}</div>
    <div class="chart">${chartSVG(points)}</div>
    <h2>Rep maxes</h2>
    <table class="rm"><thead><tr><th>Reps</th><th>Best</th><th>Date</th></tr></thead><tbody>
      ${Object.keys(rm).length ? Object.entries(rm).map(([r, s]) => `<tr><td>${r}RM</td><td><b>${fmt(s.w)}</b> ${S.unit}</td><td>${dateLabel(s.t)}</td></tr>`).join("")
        : `<tr><td colspan="3" class="muted">No sets ≤ 12 reps yet</td></tr>`}
    </tbody></table>
    <h2>History</h2>
    <ul class="sets">${recent.map((s) => `<li><span class="idx small">${new Date(s.t).toLocaleDateString(undefined, { day: "numeric", month: "short" })}</span>
      <span class="val">${fmt(s.w)} ${S.unit} × ${s.r}</span>
      <span>${s.prs.map((p) => `<span class="badge gold">${esc(p)}</span>`).join(" ")}</span>
      <button class="del" type="button" data-del="${s.id}" aria-label="Delete set">×</button></li>`).join("")}</ul>
    ${list.length > 60 ? `<p class="muted small">Showing the latest 60 sets. Export in Settings for the full history.</p>` : ""}
    <button class="btn secondary" id="logThis" type="button">Log ${esc(ex.name)}</button>
    ${ex.custom ? `<button class="btn danger" id="delEx" type="button">Delete exercise</button>` : ""}`;
  view.querySelectorAll("[data-range]").forEach((b) => (b.onclick = () => { range = b.dataset.range; renderDetail(id); }));
  view.querySelectorAll("[data-del]").forEach((b) => (b.onclick = () => deleteSet(b.dataset.del)));
  $("#logThis").onclick = () => { current = id; location.hash = "#/log"; };
  const del = $("#delEx");
  if (del) del.onclick = () => {
    if (!confirm(`Delete “${ex.name}” and its ${list.length} sets?`)) return;
    S.exercises = S.exercises.filter((e) => e.id !== id);
    S.sets = S.sets.filter((s) => s.e !== id);
    save(); location.hash = "#/progress";
  };
}

function download(name, text, type) {
  const a = document.createElement("a");
  a.href = URL.createObjectURL(new Blob([text], { type }));
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}
const stamp = () => new Date().toISOString().slice(0, 10);

function renderSettings() {
  const theme = document.documentElement.dataset.theme || "auto";
  view.innerHTML = `
    <h2>Settings</h2>
    <div class="card">
      <div class="setting"><span>Units</span><span class="toggle">${["kg", "lb"].map((u) => `<button type="button" data-unit="${u}" aria-pressed="${S.unit === u}">${u}</button>`).join("")}</span></div>
      <div class="setting"><span>Theme</span><span class="toggle">${["auto", "dark", "light"].map((t) => `<button type="button" data-theme="${t}" aria-pressed="${theme === t}">${t}</button>`).join("")}</span></div>
    </div>
    <h2>Your data</h2>
    <div class="card">
      <p class="muted small" style="margin-top:0">Everything is stored only on this device. Clearing browser data deletes it — export a backup now and then.</p>
      <p class="small num">${S.sets.length} sets · ${S.exercises.filter((e) => S.sets.some((s) => s.e === e.id)).length} exercises</p>
      <button class="btn secondary" id="expJson" type="button">Export backup (JSON)</button>
      <button class="btn secondary" id="expCsv" type="button">Export spreadsheet (CSV)</button>
      <button class="btn secondary" id="imp" type="button">Import backup</button>
      <input id="file" type="file" accept="application/json,.json" hidden>
      <button class="btn danger" id="wipe" type="button">Delete all data</button>
    </div>
    <h2>The deal</h2>
    <div class="card"><ul class="promise">
      <li>Free. Forever.</li><li>No ads.</li><li>No trackers, no analytics, no cookies.</li>
      <li>No account. Your data never leaves your phone.</li><li>Open source (MIT).</li></ul></div>
    <p class="muted small" style="text-align:center;margin:18px 0">OpenGymPR v1.0 · e1RM = Epley formula, sets ≤ 12 reps</p>`;
  view.querySelectorAll("[data-unit]").forEach((b) => (b.onclick = () => { S.unit = b.dataset.unit; save(); renderSettings(); }));
  view.querySelectorAll("[data-theme]").forEach((b) => (b.onclick = () => { S.theme = b.dataset.theme; save(); applyTheme(); renderSettings(); }));
  $("#expJson").onclick = () => download(`opengympr-backup-${stamp()}.json`, JSON.stringify(S, null, 1), "application/json");
  $("#expCsv").onclick = () => {
    const rows = [["date", "exercise", `weight_${S.unit}`, "reps", "e1rm"]].concat(
      [...S.sets].sort((a, b) => a.t - b.t).map((s) => [new Date(s.t).toISOString(), `"${exName(s.e).replace(/"/g, '""')}"`, fmt(s.w), s.r, fmt(e1rm(s.w, s.r))]));
    download(`opengympr-${stamp()}.csv`, rows.map((r) => r.join(",")).join("\n"), "text/csv");
  };
  $("#imp").onclick = () => $("#file").click();
  $("#file").onchange = async (e) => {
    const f = e.target.files[0];
    if (!f) return;
    try {
      const d = JSON.parse(await f.text());
      if (d.v !== 1 || !Array.isArray(d.sets) || !Array.isArray(d.exercises)) throw new Error();
      const valid = d.sets.filter((s) => s && typeof s.e === "string" && typeof s.w === "number" && Number.isInteger(s.r) && typeof s.t === "number");
      if (!confirm(`Replace current data with backup (${valid.length} sets)?`)) return;
      S = { ...fresh(), ...d, sets: valid.map((s) => ({ id: String(s.id || uid()), e: s.e, w: s.w, r: s.r, t: s.t })),
        exercises: d.exercises.filter((x) => x && typeof x.id === "string" && typeof x.name === "string").map((x) => ({ id: x.id, name: x.name.slice(0, 60), ...(x.custom ? { custom: true } : {}) })) };
      save(); applyTheme(); toast(`Imported ${valid.length} sets`); renderSettings();
    } catch { toast("That file is not an OpenGymPR backup."); }
  };
  $("#wipe").onclick = () => {
    if (!confirm("Delete ALL sets and custom exercises on this device? This cannot be undone.")) return;
    S = fresh(); save(); applyTheme(); toast("All data deleted"); renderSettings();
  };
}

function applyTheme() {
  if (S.theme === "auto") delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = S.theme;
}

// ---------- router ----------
function route() {
  const h = location.hash || "#/log";
  const m = h.match(/^#\/ex\/(.+)$/);
  const tab = m ? "progress" : h.slice(2);
  document.querySelectorAll(".tabs a").forEach((a) => a.setAttribute("aria-current", a.dataset.tab === tab ? "page" : "false"));
  if (m) renderDetail(decodeURIComponent(m[1]));
  else if (tab === "progress") renderProgress();
  else if (tab === "settings") renderSettings();
  else renderLog();
}
addEventListener("hashchange", () => { route(); scrollTo(0, 0); });
applyTheme();
route();

if ("serviceWorker" in navigator && location.protocol !== "file:") navigator.serviceWorker.register("sw.js").catch(() => {});
