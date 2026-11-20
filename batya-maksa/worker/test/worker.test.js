import test from "node:test";
import assert from "node:assert";
import { extractJson, normalizeSpec, buildRenderPrompt } from "../src/index.js";

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
