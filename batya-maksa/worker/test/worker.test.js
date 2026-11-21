import test from "node:test";
import assert from "node:assert";
import { extractJson, normalizeSpec, buildRenderPrompt, reconcile } from "../src/index.js";

test("extractJson tolerates prose and trailing commas", () => {
  const r = extractJson('Here: {"width_mm": 1300, "height_mm": "1400", "sashes": [{"opening":"tilt_turn"},],}');
  assert.equal(r.width_mm, 1300);
});

test("normalizeSpec clamps and defaults", () => {
  const s = normalizeSpec({ width_mm: 99999, height_mm: "abc", type: "ufo", sashes: [] });
  assert.equal(s.width_mm, 6000);
  assert.equal(s.height_mm, 1400);
  assert.equal(s.type, "single_sash");
  assert.equal(s.sashes.length, 1);
});

test("render prompt reflects spec", () => {
  const p = buildRenderPrompt(normalizeSpec({
    width_mm: 1800, height_mm: 1400, type: "double_sash",
    sashes: [{ opening: "tilt_turn" }, { opening: "fixed" }], profile: "aluminium", glass: "triple"
  }));
  assert.match(p, /aluminium/);
  assert.match(p, /triple glazing/);
  assert.match(p, /1\.80 m wide/);
  assert.match(p, /tilt-and-turn/);
});

test("type follows section count", () => {
  const s = normalizeSpec({ type: "fixed", sashes: [{ opening: "tilt_turn" }, { opening: "fixed" }] });
  assert.equal(s.type, "double_sash");
  const d = normalizeSpec({ type: "balcony_door", sashes: [{ opening: "tilt_turn" }] });
  assert.equal(d.type, "balcony_door");
});

test("cm converted to mm, sash widths fixed", () => {
  const s = normalizeSpec({ width_mm: 180, height_mm: 140, sashes: [{ width_mm: 90, opening: "turn" }, { opening: "fixed" }] });
  assert.equal(s.width_mm, 1800);
  assert.equal(s.height_mm, 1400);
  assert.deepEqual(s.sashes.map((x) => x.width_mm), [900, 900]);
});

test("agreement raises confidence, disagreement gives alternatives", () => {
  const a = normalizeSpec({ numbers: ["1800", "1400"], width_mm: 1800, height_mm: 1400, confidence: 0.6, sashes: [{ opening: "turn" }, { opening: "fixed" }] });
  const b = normalizeSpec({ numbers: ["1800", "1400"], width_mm: 1800, height_mm: 1400, confidence: 0.7, sashes: [{ opening: "turn" }, { opening: "fixed" }] });
  assert.ok(reconcile([a, b]).confidence >= 0.9);
  const c = normalizeSpec({ numbers: ["1300", "1400"], width_mm: 1300, height_mm: 1400, confidence: 0.9, sashes: [{ opening: "turn" }] });
  const r = reconcile([a, c]);
  assert.equal(r.confidence <= 0.5, true);
  assert.equal(r.alternatives.width_mm.length, 1);
  assert.equal(r.numbers, undefined);
});
