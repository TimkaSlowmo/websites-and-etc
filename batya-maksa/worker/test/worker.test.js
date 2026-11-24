import test from "node:test";
import assert from "node:assert";
import { extractJson, normalizeSpec, buildRenderPrompt, reconcile, fitSizes, parseNum, selfScore } from "../src/index.js";

test("extractJson tolerates prose and trailing commas", () => {
  const r = extractJson('Here: {"width_mm": 1300, "height_mm": "1400", "layout": {"sections": [],},}');
  assert.equal(r.width_mm, 1300);
});

test("parseNum handles comma decimals and units", () => {
  assert.equal(parseNum("1,80"), 1.8);
  assert.equal(parseNum("2 700 мм"), 2700);
  assert.equal(parseNum(null), null);
});

test("fitSizes: metres, missing values, mismatch", () => {
  assert.deepEqual(fitSizes(["0,8", "0,4"], 1200).sizes, [800, 400]);
  assert.deepEqual(fitSizes([1700, null], 2700).sizes, [1700, 1000]);
  const r = fitSizes([800, 500], 1200);
  assert.equal(r.ok, false);
  assert.equal(r.sizes.reduce((a, b) => a + b, 0), 1200);
});

test("photo 1: column split with vertical division only in the left column", () => {
  const s = normalizeSpec({
    numbers: ["1200", "2700", "1700", "1000", "800", "400"], width_mm: 1200, height_mm: 2700, product: "window",
    layout: { split: "columns", sections: [
      { size_mm: 800, cells: [{ size_mm: 1700, opening: "tilt_turn", hinge: "left" }, { size_mm: 1000, opening: "fixed" }] },
      { size_mm: 400, cells: [{ size_mm: 2700, opening: "fixed" }] },
    ] },
    system: "BKT 77", fill_mm: "32", color: "RAL 7024",
    info: [{ label: "Система", value: "BKT 77" }, { label: "Заполнение", value: "32" }],
  });
  assert.equal(s.layout.split, "columns");
  assert.deepEqual(s.layout.sections.map((x) => x.size_mm), [800, 400]);
  assert.deepEqual(s.layout.sections[0].cells.map((x) => x.size_mm), [1700, 1000]);
  assert.equal(s.layout.sections[0].cells[0].opening, "tilt_turn");
  assert.equal(s.layout.sections[0].cells[0].hinge, "left");
  assert.equal(s.layout.sections[1].cells[0].hinge, "none");
  assert.equal(s.fill_mm, 32);
  assert.equal(s.info.length, 2);
  assert.equal(s._layout_ok, true);
  assert.equal(selfScore(s), 4);
  assert.equal(s.sashes.length, 3); // старый формат для прежних версий
});

test("photo 4: door with transom (rows) — bottom opening cell becomes a door leaf", () => {
  const s = normalizeSpec({
    width_mm: 1000, height_mm: 2000, product: "door",
    layout: { split: "rows", sections: [{ size_mm: 700, cells: [{ opening: "tilt" }] }, { size_mm: null, cells: [{ opening: "turn" }] }] },
  });
  assert.deepEqual(s.layout.sections.map((x) => x.size_mm), [700, 1300]);
  assert.equal(s.layout.sections[0].cells[0].hinge, "bottom");
  assert.equal(s.layout.sections[0].cells[0].door, false);
  assert.equal(s.layout.sections[1].cells[0].door, true);
  assert.equal(s.layout.sections[1].cells[0].size_mm, 1000);
});

test("metres on the drawing (photo 2 style) are converted", () => {
  const s = normalizeSpec({ width_mm: "1,80", height_mm: "1,60", layout: { split: "columns", sections: [{ size_mm: 1.8, cells: [{}] }] } });
  assert.equal(s.width_mm, 1800);
  assert.equal(s.height_mm, 1600);
  assert.equal(s.layout.sections[0].cells[0].size_mm, 1600);
});

test("legacy sashes are converted to columns; garbage is safe", () => {
  const s = normalizeSpec({ width_mm: 1800, height_mm: 1400, sashes: [{ width_mm: 900, opening: "turn" }, { opening: "fixed" }] });
  assert.equal(s.layout.sections.length, 2);
  assert.deepEqual(s.layout.sections.map((x) => x.size_mm), [900, 900]);
  const g = normalizeSpec({ width_mm: "abc", layout: "nonsense", product: "ufo", fill_mm: 500, quantity: -3 });
  assert.equal(g.width_mm, 1300);
  assert.equal(g.product, "window");
  assert.equal(g.fill_mm, null);
  assert.equal(g.quantity, 1);
});

test("reconcile merges info and gives alternatives on disagreement", () => {
  const base = { width_mm: 1200, height_mm: 2700, layout: { split: "columns", sections: [{ size_mm: 800, cells: [{}, {}] }, { size_mm: 400, cells: [{}] }] } };
  const a = normalizeSpec({ ...base, numbers: ["1200", "2700"], confidence: 0.6, info: [{ label: "Система", value: "BKT 77" }] });
  const b = normalizeSpec({ ...base, numbers: ["1200", "2700"], confidence: 0.7, info: [{ label: "Цвет", value: "RAL 7024" }], color: "RAL 7024" });
  const r = reconcile([a, b]);
  assert.ok(r.confidence >= 0.9);
  assert.equal(r.info.length, 2);
  assert.equal(r.color, "RAL 7024");
  assert.equal(r._layout_ok, undefined);
  const c = normalizeSpec({ ...base, width_mm: 1700, numbers: ["1700"] });
  const r2 = reconcile([a, c]);
  assert.ok(r2.confidence <= 0.5);
  assert.equal(r2.alternatives.width_mm.length, 1);
});

test("render prompt describes the grid and colour", () => {
  const s = normalizeSpec({
    width_mm: 1200, height_mm: 2700, color: "RAL 7024",
    layout: { split: "columns", sections: [{ size_mm: 800, cells: [{ size_mm: 1700, opening: "tilt_turn" }, { size_mm: 1000 }] }, { size_mm: 400, cells: [{}] }] },
  });
  const p = buildRenderPrompt(s);
  assert.match(p, /graphite grey/);
  assert.match(p, /left 0\.80 m wide column: upper 1\.70 m tilt-and-turn sash and lower 1\.00 m fixed glazing/);
  assert.match(p, /right 0\.40 m wide column/);
});

import { chainFix, explicitQuantity } from "../src/index.js";

test("chain fix: misread 1200 as 7200 is corrected by 800+400", () => {
  const raw = { numbers: ["7200", "1700", "1000", "800", "400", "2700"], layout: { split: "columns", sections: [{ size_mm: 800, cells: [{ size_mm: 1700 }, { size_mm: 1000 }] }, { size_mm: 400, cells: [{}] }] } };
  assert.deepEqual(chainFix(raw, 7200, 2700), { W: 1200, H: 2700 });
  const s = normalizeSpec({ ...raw, width_mm: 7200, height_mm: 2700 });
  assert.equal(s.width_mm, 1200);
  assert.deepEqual(s.layout.sections.map((x) => x.size_mm), [800, 400]);
});

test("quantity only when written explicitly", () => {
  assert.equal(explicitQuantity({ quantity: 61, notes: "6. Схема открывания" }), 1);
  assert.equal(explicitQuantity({ quantity: 3, notes: "окно 3 шт" }), 3);
  assert.equal(explicitQuantity({ quantity: 4, info: [{ label: "Количество", value: "4" }] }), 4);
});

import { mergeOpenings, cellRects } from "../src/index.js";

test("photo 3: simpler grid wins, openings transferred by overlap", () => {
  const gemma = normalizeSpec({ numbers: ["1200", "2700", "1350", "400", "800"], width_mm: 1200, height_mm: 2700, layout: { split: "rows", sections: [
    { size_mm: 1350, cells: [{ size_mm: 400 }, { size_mm: 800, opening: "tilt" }] },
    { size_mm: 1350, cells: [{ size_mm: 400 }, { size_mm: 800, opening: "top_hung" }] }] } });
  gemma.model = "@cf/google/gemma-4-26b-a4b-it";
  const mistral = normalizeSpec({ numbers: ["1200", "2700", "1350", "400", "800"], width_mm: 1200, height_mm: 2700, layout: { split: "columns", sections: [
    { size_mm: 400, cells: [{ size_mm: 1350 }, { size_mm: 1350 }] }, { size_mm: 800, cells: [{ size_mm: 2700 }] }] } });
  mistral.model = "@cf/mistralai/mistral-small-3.1-24b-instruct";
  const r = reconcile([gemma, mistral]);
  assert.equal(r.layout.split, "columns");
  assert.deepEqual(r.layout.sections.map((x) => x.size_mm), [400, 800]);
  assert.notEqual(r.layout.sections[1].cells[0].opening, "fixed");
  assert.equal(r.layout.sections[0].cells[0].opening, "fixed");
});

test("cell rectangles", () => {
  const s = normalizeSpec({ width_mm: 1200, height_mm: 2700, layout: { split: "columns", sections: [{ size_mm: 800, cells: [{ size_mm: 1700 }, { size_mm: 1000 }] }, { size_mm: 400, cells: [{}] }] } });
  assert.deepEqual(cellRects(s).map((r) => [r.x, r.y, r.w, r.h]), [[0, 0, 800, 1700], [0, 1700, 800, 1000], [800, 0, 400, 2700]]);
});
