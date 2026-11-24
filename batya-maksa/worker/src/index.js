// Cloudflare Worker: ИИ-бэкенд приложения «Проём».
// POST /analyze  {image: base64 jpeg}  -> {spec}
// POST /render   {spec}                -> {image: base64 jpeg}
// Без токена: защита — лимит запросов с IP и ограничение размера изображения.

// Первые две работают параллельно: Gemma 4 лучше читает надписи и символы, Mistral — сетку и размеры.
export const VISION_MODELS = [
  "@cf/google/gemma-4-26b-a4b-it",
  "@cf/mistralai/mistral-small-3.1-24b-instruct",
  "@cf/meta/llama-4-scout-17b-16e-instruct",
  "@cf/google/gemma-3-12b-it",
];
const LEGACY_VISION_MODEL = "@cf/meta/llama-3.2-11b-vision-instruct";
const IMAGE_MODEL = "@cf/black-forest-labs/flux-1-schnell";

export const PRODUCTS = ["window", "door", "balcony_block", "facade", "partition"];
export const OPENINGS = ["fixed", "turn", "tilt", "tilt_turn", "top_hung", "slide"];
export const HINGES = ["left", "right", "top", "bottom", "none"];

const CORS = {};
const json = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json", ...CORS } });

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    if (env.LIMITER) {
      const ip = request.headers.get("CF-Connecting-IP") || "anon";
      const { success } = await env.LIMITER.limit({ key: ip });
      if (!success) return json({ error: "rate_limited", message: "Слишком много запросов, подождите минуту" }, 429);
    }
    const path = new URL(request.url).pathname;
    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "bad_json" }, 400);
    }
    try {
      if (path === "/analyze" && Array.isArray(body.debug_models)) return json(await analyzeDebug(env, body.image, body.debug_models, body.debug_schema !== false, body.debug_extra));
      if (path === "/analyze") return json({ spec: await analyze(env, body.image) });
      if (path === "/render") return json({ image: await render(env, body.spec) });
      return json({ error: "not_found" }, 404);
    } catch (e) {
      return json({ error: "ai_failed", message: String(e && e.message ? e.message : e) }, 502);
    }
  },
};

// ---------- /analyze ----------

export const ANALYZE_PROMPT = `You are an estimator at an aluminium window and door factory. The photo shows a hand-drawn sketch of ONE product (window, door, balcony block, facade / stained-glass, partition) with dimensions in millimetres (sometimes centimetres or metres, e.g. "1,80" = 1.80 m), and often extra notes next to the drawing: profile system ("BKT 77", "BKF 50"), filling / glass unit thickness ("Заполнение 32"), colour ("RAL 7024"), opening scheme, quantity, checklists.

Return ONLY a JSON object, no markdown:
{
 "observations": string — FIRST describe in words what you see: the outer rectangle, every line that crosses the whole frame, every shorter line and which part it divides, every dimension and which edge it belongs to, every opening symbol (where its lines meet) and every note next to the drawing,
 "numbers": [string]  — then copy every number written on the sheet exactly as written, digit by digit,
 "width_mm": number   — overall width,
 "height_mm": number  — overall height,
 "product": one of ${JSON.stringify(PRODUCTS)},
 "layout": {
   "split": "columns" if the frame is first divided by vertical lines running the FULL height into sections left→right; "rows" if first divided by horizontal lines running the FULL width into sections top→bottom,
   "sections": [ {
      "size_mm": section width (columns) or height (rows),
      "cells": [ {
         "size_mm": cell height (columns: cells top→bottom) or width (rows: cells left→right),
         "opening": one of ${JSON.stringify(OPENINGS)},
         "hinge": one of ${JSON.stringify(HINGES)},
         "door": true if this cell is a door leaf
      } ]
   } ]
 },
 "system": profile system exactly as written (e.g. "BKT 77"), "" if not written,
 "fill_mm": filling / glass unit thickness in mm as a number, or null,
 "glass": glass formula if written (e.g. "4-10-4-10-4"), else "",
 "color": outside colour as written (e.g. "RAL 7024"), else "",
 "color_inside": inside colour if a different one is written, else "",
 "inward": false only if it says the sashes open outward ("наружу"), else true,
 "quantity": number of identical products (default 1),
 "info": [ {"label": string, "value": string} ] — EVERY piece of additional information written next to the drawing, in Russian, e.g. {"label":"Система","value":"BKT 77"}, {"label":"Заполнение","value":"32 мм"}, {"label":"Цвет","value":"RAL 7024"}, {"label":"Схема открывания","value":"поворотно-откидная, петли слева"},
 "notes": ALL text written on the sheet, copied verbatim in the original language,
 "confidence": number 0..1
}

How to read the layout:
- Lines that cross the whole frame define the sections. A line that divides only ONE section (e.g. a short horizontal line inside the left column that does not continue into the right column) splits that section into cells. Example: 1200 wide, 2700 high, left column 800 split into 1700 (top) + 1000 (bottom), right column 400 not split → split "columns", sections [ {800, cells [1700, 1000]}, {400, cells [2700]} ].
- Example: door 1000×2000 with a 700 transom on top → split "rows", sections [ {700, cells [1000]}, {1300, cells [1000 door]} ].
- Dimension chains are written along the edges; section sizes must add up to the width (columns) or height (rows), and cell sizes must add up to the other dimension.
- Opening symbols: two lines from the corners of a pane meeting at a point (apex). The apex points to the HANDLE side, the hinges are on the opposite side. Apex at the left or right edge → "turn", hinge on the other side. Apex at the top edge → "tilt", hinge "bottom". Apex at the bottom edge → "top_hung", hinge "top". A side apex AND a top apex in one pane → "tilt_turn", hinge = the side hinge. No lines → "fixed", hinge "none". Arrows → "slide".
- A cell that reaches the floor in a door product and opens is a door leaf ("door": true).
- Checklists like "1. Высота ✓ 2. Ширина ✓ 3. Система BKT 77 ..." — put every item with its value into "info".
- Handwritten digits: 1 vs 7, 3 vs 8, 5 vs 6, 0 vs 6 — check carefully. If a value is not visible, make the most reasonable guess and lower "confidence". Output JSON only.`;

const CELL_SCHEMA = {
  type: "object",
  properties: {
    size_mm: { type: ["number", "null"] },
    opening: { type: "string", enum: OPENINGS },
    hinge: { type: "string", enum: HINGES },
    door: { type: "boolean" },
  },
  required: ["opening"],
};

export const SPEC_SCHEMA = {
  type: "object",
  properties: {
    observations: { type: "string" },
    numbers: { type: "array", items: { type: "string" } },
    width_mm: { type: "number" },
    height_mm: { type: "number" },
    product: { type: "string", enum: PRODUCTS },
    layout: {
      type: "object",
      properties: {
        split: { type: "string", enum: ["columns", "rows"] },
        sections: {
          type: "array",
          items: {
            type: "object",
            properties: { size_mm: { type: ["number", "null"] }, cells: { type: "array", items: CELL_SCHEMA } },
            required: ["cells"],
          },
        },
      },
      required: ["split", "sections"],
    },
    system: { type: "string" },
    fill_mm: { type: ["number", "null"] },
    glass: { type: "string" },
    color: { type: "string" },
    color_inside: { type: "string" },
    inward: { type: "boolean" },
    quantity: { type: "number" },
    info: {
      type: "array",
      items: { type: "object", properties: { label: { type: "string" }, value: { type: "string" } }, required: ["label", "value"] },
    },
    notes: { type: "string" },
    confidence: { type: "number" },
  },
  required: ["observations", "numbers", "width_mm", "height_mm", "product", "layout"],
};

const responseText = (res) =>
  typeof res === "string"
    ? res
    : (res && (res.response ?? res.result?.response ?? res.description ?? res.choices?.[0]?.message?.content)) || "";

// Настройки моделей: Gemma 4 по умолчанию долго рассуждает — ограничиваем.
const MODEL_EXTRA = {
  "@cf/google/gemma-4-26b-a4b-it": { reasoning_effort: "low", chat_template_kwargs: { enable_thinking: false } },
};

async function tryModel(env, model, dataUri, withSchema, extra) {
  const input = {
    messages: [
      { role: "system", content: "You read window/door shop drawings and reply with JSON only." },
      {
        role: "user",
        content: [
          { type: "text", text: ANALYZE_PROMPT },
          { type: "image_url", image_url: { url: dataUri } },
        ],
      },
    ],
    max_tokens: 2500,
    temperature: 0.1,
  };
  if (withSchema) input.response_format = { type: "json_schema", json_schema: SPEC_SCHEMA };
  Object.assign(input, MODEL_EXTRA[model] || {}, extra || {});
  const res = await env.AI.run(model, input);
  const out = res && typeof res.response === "object" && res.response !== null ? res.response : responseText(res);
  return extractJson(out);
}

async function tryLegacy(env, bytes) {
  const run = () =>
    env.AI.run(LEGACY_VISION_MODEL, { image: [...bytes], prompt: ANALYZE_PROMPT, max_tokens: 1200, temperature: 0.1 });
  let res;
  try {
    res = await run();
  } catch (e) {
    if (!/agree/i.test(String(e && e.message))) throw e;
    await env.AI.run(LEGACY_VISION_MODEL, { prompt: "agree" });
    res = await run();
  }
  return extractJson(responseText(res));
}

async function runModel(env, model, dataUri) {
  try {
    return await tryModel(env, model, dataUri, true);
  } catch (e) {
    return await tryModel(env, model, dataUri, false);
  }
}

const withTimeout = (p, ms) =>
  Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error("timeout")), ms))]);

async function analyze(env, imageB64) {
  if (!imageB64) throw new Error("image is required");
  const bytes = b64ToBytes(imageB64);
  if (bytes.length > 5 * 1024 * 1024) throw new Error("image too large");
  const dataUri = "data:image/jpeg;base64," + String(imageB64).replace(/^data:[^,]+,/, "");
  const started = Date.now();

  // Две модели параллельно: сверяем цифры и сетку. Вторую ждём не дольше 6 с после первой.
  const [m1, m2] = VISION_MODELS;
  const p1 = withTimeout(runModel(env, m1, dataUri), 40000).then((r) => ({ r, m: m1 }));
  const p2 = withTimeout(runModel(env, m2, dataUri), 40000).then((r) => ({ r, m: m2 }));
  const results = [];
  const first = await Promise.any([p1, p2]).catch(() => null);
  if (first) {
    results.push(first);
    const other = first.m === m1 ? p2 : p1;
    const second = await withTimeout(other, 10000).catch(() => null);
    if (second) results.push(second);
  }
  if (results.length === 0) {
    for (const m of VISION_MODELS.slice(2)) {
      try {
        results.push({ r: await withTimeout(runModel(env, m, dataUri), 40000), m });
        break;
      } catch {}
    }
  }
  if (results.length === 0) {
    try {
      results.push({ r: await tryLegacy(env, bytes), m: LEGACY_VISION_MODEL });
    } catch {}
  }
  if (results.length === 0) {
    const empty = normalizeSpec({});
    delete empty.numbers;
    delete empty._layout_ok;
    return {
      ...empty,
      confidence: 0,
      notes: "ИИ не смог прочитать чертёж. Проверьте размеры и параметры вручную.",
      model: "none",
      ms: Date.now() - started,
    };
  }
  const specs = results.map(({ r, m }) => ({ ...normalizeSpec(r), model: m }));
  return { ...reconcile(specs), ms: Date.now() - started };
}

/** Для отладки: прогнать выбранные модели по очереди и вернуть нормализованный ответ каждой. */
async function analyzeDebug(env, imageB64, models, withSchema, extra) {
  const dataUri = "data:image/jpeg;base64," + String(imageB64).replace(/^data:[^,]+,/, "");
  const out = [];
  for (const m of models.filter((x) => VISION_MODELS.includes(x)).slice(0, 3)) {
    const t = Date.now();
    try {
      const raw = await withTimeout(tryModel(env, m, dataUri, withSchema, extra && typeof extra === "object" ? extra : null), 110000);
      out.push({ model: m, ms: Date.now() - t, observations: String(raw.observations || "").slice(0, 1500), spec: normalizeSpec(raw) });
    } catch (e) {
      let rawText = "";
      try {
        const res = await withTimeout(env.AI.run(m, {
          messages: [{ role: "user", content: [{ type: "text", text: ANALYZE_PROMPT }, { type: "image_url", image_url: { url: dataUri } }] }],
          max_tokens: 2500, temperature: 0.1,
        }), 110000);
        rawText = JSON.stringify(res).slice(0, 4000);
      } catch (e2) { rawText = "raw failed: " + String(e2 && e2.message); }
      out.push({ model: m, ms: Date.now() - t, error: String(e && e.message), raw: rawText });
    }
  }
  return { results: out };
}

export function extractJson(text) {
  if (text && typeof text === "object") return text;
  const s = String(text || "");
  const start = s.indexOf("{");
  const end = s.lastIndexOf("}");
  if (start === -1 || end <= start) throw new Error("model returned no JSON");
  const raw = s.slice(start, end + 1).replace(/,\s*([}\]])/g, "$1");
  return JSON.parse(raw);
}

// ---------- нормализация ----------

/** Число из числа или строки ("1,80", "1 800", "2700мм"). */
export function parseNum(v) {
  if (typeof v === "number") return Number.isFinite(v) ? v : null;
  if (typeof v !== "string") return null;
  const m = v.replace(/\s/g, "").replace(",", ".").match(/-?\d+(\.\d+)?/);
  return m ? Number(m[0]) : null;
}

/** Подгоняет размеры частей под общий размер: единицы, пропуски, сумма. Возвращает {sizes, ok}. */
export function fitSizes(values, total) {
  let nums = values.map((v) => {
    const n = parseNum(v);
    return n && n > 0 ? n : null;
  });
  const known = nums.filter((n) => n !== null);
  let ok = known.length === nums.length;
  if (known.length) {
    const sum = known.reduce((a, b) => a + b, 0);
    let k = 1;
    // Размеры в метрах или сантиметрах при общем размере в мм.
    if (Math.max(...known) < 10 && total >= 200) k = 1000;
    else if (sum * 10 <= total * 1.05 && Math.max(...known) < 400 && total >= 400) k = 10;
    nums = nums.map((n) => (n === null ? null : n * k));
  }
  const knownSum = nums.reduce((a, b) => a + (b || 0), 0);
  const missing = nums.filter((n) => n === null).length;
  if (missing > 0) {
    const rem = total - knownSum;
    if (rem > 50 * missing) nums = nums.map((n) => (n === null ? rem / missing : n));
    else nums = nums.map(() => total / nums.length);
  } else if (Math.abs(knownSum - total) / total > 0.02) {
    ok = false;
    nums = nums.map((n) => (n * total) / knownSum);
  }
  const ints = nums.map((n) => Math.max(1, Math.round(n)));
  ints[ints.length - 1] += total - ints.reduce((a, b) => a + b, 0);
  return { sizes: ints, ok };
}

const OPENING_ALIASES = {
  tilt_and_turn: "tilt_turn", "tilt-turn": "tilt_turn", tiltturn: "tilt_turn", side_hung: "turn", casement: "turn",
  bottom_hung: "tilt", hopper: "tilt", awning: "top_hung", top: "top_hung", sliding: "slide", none: "fixed",
};
const normOpening = (v) => {
  const s = String(v || "fixed").toLowerCase().trim();
  const o = OPENING_ALIASES[s] || s;
  return OPENINGS.includes(o) ? o : "fixed";
};
const defaultHinge = (opening) =>
  ({ turn: "left", tilt_turn: "left", tilt: "bottom", top_hung: "top", slide: "none", fixed: "none" })[opening];
function normHinge(v, opening) {
  if (opening === "fixed" || opening === "slide") return "none";
  if (opening === "tilt") return "bottom";
  if (opening === "top_hung") return "top";
  const h = String(v || "").toLowerCase();
  return h === "left" || h === "right" ? h : defaultHinge(opening);
}

export function normalizeLayout(raw, W, H, legacySashes, product, inward) {
  const split = raw && raw.split === "rows" ? "rows" : "columns";
  let secs = raw && Array.isArray(raw.sections) ? raw.sections.filter((x) => x && typeof x === "object").slice(0, 8) : [];
  if (secs.length === 0 && Array.isArray(legacySashes) && legacySashes.length) {
    secs = legacySashes.slice(0, 8).map((s) => ({ size_mm: s && s.width_mm, cells: [{ opening: s && s.opening }] }));
    return normalizeLayout({ split: "columns", sections: secs }, W, H, null, product, inward);
  }
  if (secs.length === 0) secs = [{ size_mm: split === "columns" ? W : H, cells: [{ opening: "fixed" }] }];
  const main = split === "columns" ? W : H;
  const cross = split === "columns" ? H : W;
  const top = fitSizes(secs.map((s) => s.size_mm), main);
  let ok = top.ok;
  const sections = secs.map((s, i) => {
    let cells = Array.isArray(s.cells) ? s.cells.filter((c) => c && typeof c === "object").slice(0, 8) : [];
    if (cells.length === 0) cells = [{ opening: "fixed" }];
    const fit = fitSizes(cells.map((c) => c.size_mm), cross);
    ok = ok && fit.ok;
    return {
      size_mm: top.sizes[i],
      cells: cells.map((c, j) => {
        const opening = normOpening(c.opening);
        const touchesFloor = split === "columns" ? j === cells.length - 1 : i === secs.length - 1;
        const cellHeight = split === "columns" ? fit.sizes[j] : top.sizes[i];
        const door = opening !== "fixed" && (c.door === true || (product === "door" && touchesFloor && cellHeight >= 1000));
        return { size_mm: fit.sizes[j], opening, hinge: normHinge(c.hinge, opening), inward: c.inward === false ? false : inward, door };
      }),
    };
  });
  return { layout: { split, sections }, ok };
}

/** Сумма известных размеров с переводом единиц к мм (относительно общего размера). */
function chainSum(values, total) {
  const nums = (values || []).map((v) => parseNum(v));
  if (nums.length < 2 || nums.some((n) => !(n > 0))) return null;
  let sum = nums.reduce((a, b) => a + b, 0);
  if (Math.max(...nums) < 10) sum *= 1000;
  else if (Math.max(...nums) < 400 && sum * 10 <= total * 1.5) sum *= 10;
  return Math.round(sum);
}

export function chainFix(raw, W, H) {
  const lay = raw && raw.layout;
  if (!lay || !Array.isArray(lay.sections)) return { W, H };
  const nums = (Array.isArray(raw.numbers) ? raw.numbers : []).map(parseNum).filter((x) => x !== null);
  const written = (v) => nums.some((n) => Math.abs(n - v) < 1 || Math.abs(n * 10 - v) < 1 || Math.abs(n * 1000 - v) < 1);
  // Цепочке верим, если все её части выписаны на чертеже, или выписана её сумма, или общий размер не найден среди цифр.
  const fix = (parts, sum, cur, lo, hi) => {
    if (!sum || sum < lo || sum > hi) return cur;
    if (Math.abs(sum - cur) / cur <= 0.03) return cur;
    const partsWritten = parts.every((p) => written(parseNum(p)));
    return partsWritten || written(sum) || !written(cur) ? sum : cur;
  };
  const cols = lay.split !== "rows";
  const mainParts = lay.sections.map((x) => x && x.size_mm);
  const mainSum = chainSum(mainParts, cols ? W : H);
  if (cols) W = fix(mainParts, mainSum, W, 200, 12000);
  else H = fix(mainParts, mainSum, H, 200, 6000);
  // Поперечный размер: по самой подробной секции (больше всего ячеек).
  const richest = [...lay.sections].filter((x) => x && Array.isArray(x.cells)).sort((a, b) => b.cells.length - a.cells.length)[0];
  if (richest && richest.cells.length > 1) {
    const crossParts = richest.cells.map((c) => c && c.size_mm);
    const crossSum = chainSum(crossParts, cols ? H : W);
    if (cols) H = fix(crossParts, crossSum, H, 200, 6000);
    else W = fix(crossParts, crossSum, W, 200, 12000);
  }
  return { W, H };
}

/** Количество > 1 — только если на чертеже прямо написано «N шт» / «кол-во N». */
export function explicitQuantity(raw) {
  const q = Math.round(parseNum(raw.quantity) || 1);
  if (q <= 1) return 1;
  const text = [raw.notes, ...(Array.isArray(raw.info) ? raw.info.map((x) => `${x && x.label} ${x && x.value}`) : [])].join(" ").toLowerCase();
  const re = new RegExp(`(\\b${q}\\s*(шт|штук|pcs|изд))|((кол-?во|количество|qty)[^\\d]{0,12}${q}\\b)`);
  return re.test(text) && q <= 999 ? q : 1;
}

const LEGACY_PRODUCT = { balcony_door: "door", entrance_door: "door", interior_door: "door", balcony_block: "balcony_block" };

export function normalizeSpec(raw) {
  raw = raw || {};
  let w = parseNum(raw.width_mm);
  let h = parseNum(raw.height_mm);
  let k = 1;
  if (w > 0 && h > 0) {
    if (w < 10 && h < 10) k = 1000;
    else if (w < 400 && h < 400) k = 10;
  }
  const clamp = (v, min, max, fb) => (Number.isFinite(v) && v > 0 ? Math.min(max, Math.max(min, Math.round(v))) : fb);
  let W = clamp(w * k, 200, 12000, 1300);
  let H = clamp(h * k, 200, 6000, 1400);

  // Общий размер сверяем с суммой цепочки: если сумма секций выписана на чертеже, а общий размер — нет, верим цепочке.
  ({ W, H } = chainFix(raw, W, H));

  let product = String(raw.product || "").toLowerCase();
  if (!PRODUCTS.includes(product)) product = LEGACY_PRODUCT[raw.type] || "window";
  const inward = raw.inward === false ? false : true;
  const { layout, ok } = normalizeLayout(raw.layout, W, H, raw.sashes, product, inward);

  let fill = parseNum(raw.fill_mm);
  if (!(fill >= 2 && fill <= 80)) fill = null;
  const info = (Array.isArray(raw.info) ? raw.info : [])
    .filter((x) => x && (x.label || x.value))
    .slice(0, 24)
    .map((x) => ({ label: String(x.label || "").slice(0, 60), value: String(x.value ?? "").slice(0, 160) }));
  const quantity = explicitQuantity(raw);

  // Поля старого формата — для установленных ранее версий приложения.
  const opened = layout.sections.flatMap((s) =>
    s.cells.map((c) => ({ width_mm: layout.split === "columns" ? s.size_mm : c.size_mm, opening: c.opening === "top_hung" ? "tilt" : c.opening }))
  );
  const n = layout.split === "columns" ? layout.sections.length : Math.max(...layout.sections.map((s) => s.cells.length));
  const legacyType = product === "door" ? "entrance_door" : product === "balcony_block" ? "balcony_block" : n >= 3 ? "triple_sash" : n === 2 ? "double_sash" : opened.every((x) => x.opening === "fixed") ? "fixed" : "single_sash";

  return {
    numbers: (Array.isArray(raw.numbers) ? raw.numbers : []).map(String).slice(0, 40),
    width_mm: W,
    height_mm: H,
    product,
    quantity,
    layout,
    system: String(raw.system || "").slice(0, 60),
    fill_mm: fill,
    glass: String(raw.glass || "").slice(0, 60),
    color: String(raw.color || "").slice(0, 60),
    color_inside: String(raw.color_inside || "").slice(0, 60),
    inward,
    info,
    notes: String(raw.notes || "").slice(0, 800),
    extras: (Array.isArray(raw.extras) ? raw.extras : []).map(String).slice(0, 10),
    confidence: Math.min(1, Math.max(0, Number(raw.confidence) || 0.5)),
    type: legacyType,
    sashes: opened,
    _layout_ok: ok,
  };
}

/** Согласованность ответа (0..4): размеры есть среди выписанных цифр, сетка сходится без подгонки. */
export function selfScore(s) {
  const nums = (s.numbers || []).map((x) => parseNum(x)).filter((x) => x !== null);
  const has = (v) => nums.some((n) => n === v || n * 10 === v || n * 1000 === v);
  let score = 0;
  if (has(s.width_mm)) score += 1;
  if (has(s.height_mm)) score += 1;
  if (s._layout_ok) score += 1;
  if (s.layout.sections.length > 1 || s.layout.sections.some((x) => x.cells.length > 1)) score += 1;
  return score;
}

/** Прямоугольники ячеек в мм (x, y от левого верхнего угла). */
export function cellRects(spec) {
  const lay = spec.layout;
  const out = [];
  let a = 0;
  for (const sec of lay.sections) {
    let b = 0;
    for (const c of sec.cells) {
      out.push(lay.split === "columns" ? { x: a, y: b, w: sec.size_mm, h: c.size_mm, c } : { x: b, y: a, w: c.size_mm, h: sec.size_mm, c });
      b += c.size_mm;
    }
    a += sec.size_mm;
  }
  return out;
}

/** Переносит открывание из другой сетки по перекрытию (ячейка пустая — берём створку, которую увидела другая модель). */
export function mergeOpenings(target, source) {
  const kx = target.width_mm / source.width_mm;
  const ky = target.height_mm / source.height_mm;
  const src = cellRects(source).map((r) => ({ ...r, x: r.x * kx, y: r.y * ky, w: r.w * kx, h: r.h * ky }));
  for (const t of cellRects(target)) {
    if (t.c.opening !== "fixed") continue;
    let best = null;
    let bestArea = 0;
    for (const r of src) {
      if (r.c.opening === "fixed") continue;
      const ix = Math.max(0, Math.min(t.x + t.w, r.x + r.w) - Math.max(t.x, r.x));
      const iy = Math.max(0, Math.min(t.y + t.h, r.y + r.h) - Math.max(t.y, r.y));
      const area = ix * iy;
      if (area > bestArea) { bestArea = area; best = r; }
    }
    if (best && bestArea >= 0.4 * Math.min(t.w * t.h, best.w * best.h)) {
      t.c.opening = best.c.opening;
      t.c.hinge = best.c.hinge;
      t.c.door = best.c.door;
    }
  }
  return target;
}

const cellCount = (s) => s.layout.sections.reduce((n, x) => n + x.cells.length, 0);
const close = (a, b) => Math.abs(a - b) / Math.max(a, b) < 0.03;
const layoutKey = (l) => l.split + ":" + l.sections.map((s) => s.cells.length).join(",");

/** Сверка ответов моделей: согласие повышает уверенность, расхождение даёт варианты на выбор, доп. информация объединяется. */
export function reconcile(specs) {
  const order = (s) => (VISION_MODELS.indexOf(s.model) + 1 || 99);
  let ranked = [...specs].sort((a, b) => selfScore(b) - selfScore(a) || order(a) - order(b));
  // Обе сетки сходятся, но разные: берём более простую (меньше ячеек — меньше лишних импостов).
  if (ranked.length > 1 && ranked[0]._layout_ok && ranked[1]._layout_ok && layoutKey(ranked[0].layout) !== layoutKey(ranked[1].layout)
      && close(ranked[0].width_mm, ranked[1].width_mm) && close(ranked[0].height_mm, ranked[1].height_mm)
      && cellCount(ranked[1]) < cellCount(ranked[0])) {
    ranked = [ranked[1], ranked[0], ...ranked.slice(2)];
  }
  const best = JSON.parse(JSON.stringify(ranked[0]));
  delete best.numbers;
  delete best._layout_ok;
  if (ranked.length < 2) {
    best.confidence = Math.min(best.confidence, selfScore(ranked[0]) >= 3 ? 0.85 : 0.6);
    return best;
  }
  const other = ranked[1];
  // Доп. информация и текст: объединяем, чтобы ничего не потерять.
  const labels = new Set(best.info.map((x) => x.label.toLowerCase()));
  best.info = [...best.info, ...other.info.filter((x) => !labels.has(x.label.toLowerCase()))].slice(0, 24);
  if ((other.notes || "").length > (best.notes || "").length) best.notes = other.notes;
  for (const f of ["system", "glass", "color", "color_inside"]) if (!best[f] && other[f]) best[f] = other[f];
  if (best.fill_mm == null && other.fill_mm != null) best.fill_mm = other.fill_mm;
  if (best.product === "window" && other.product !== "window") best.product = other.product;
  mergeOpenings(best, other);
  best.sashes = best.layout.sections.flatMap((s) =>
    s.cells.map((c) => ({ width_mm: best.layout.split === "columns" ? s.size_mm : c.size_mm, opening: c.opening === "top_hung" ? "tilt" : c.opening })));

  const wOk = close(best.width_mm, other.width_mm);
  const hOk = close(best.height_mm, other.height_mm);
  const gridOk = layoutKey(best.layout) === layoutKey(other.layout);
  if (wOk && hOk) {
    best.confidence = gridOk ? Math.max(best.confidence, 0.9) : Math.min(Math.max(best.confidence, 0.7), 0.75);
    return best;
  }
  best.confidence = Math.min(best.confidence, 0.5);
  best.alternatives = { width_mm: wOk ? [] : [other.width_mm], height_mm: hOk ? [] : [other.height_mm] };
  return best;
}

// ---------- /render ----------

const OPENING_TEXT = {
  fixed: "fixed glazing",
  tilt: "tilt-in (bottom-hinged) sash",
  turn: "side-hung opening sash",
  tilt_turn: "tilt-and-turn sash",
  top_hung: "top-hung vent",
  slide: "sliding sash",
};
const RAL_EN = {
  "9016": "traffic white", "9010": "pure white", "9003": "signal white", "9001": "cream", "7035": "light grey",
  "7040": "window grey", "7001": "silver grey", "7012": "basalt grey", "7015": "slate grey", "7024": "graphite grey",
  "7016": "anthracite grey", "7021": "black grey", "9005": "jet black", "9006": "white aluminium", "9007": "grey aluminium",
  "8017": "chocolate brown", "8019": "grey brown", "8014": "sepia brown", "3005": "wine red", "6005": "moss green",
};

function colourText(c) {
  const s = String(c || "");
  const ral = s.match(/(\d{4})/);
  if (/ral/i.test(s) && ral) return `RAL ${ral[1]} ${RAL_EN[ral[1]] || ""} powder-coated`.replace(/\s+/g, " ").trim();
  if (/дуб|oak|дерев|wood|орех|махагон/i.test(s)) return "wood-grain decor";
  if (/анод|anod/i.test(s)) return "anodised silver";
  if (/черн|чёрн|black/i.test(s)) return "matte black";
  if (/антрацит|anthrac/i.test(s)) return "anthracite grey";
  if (/бел|white/i.test(s) || !s) return "white";
  return s;
}

export function buildRenderPrompt(spec) {
  const W = spec.width_mm || 1300;
  const H = spec.height_mm || 1400;
  const m = (v) => (v / 1000).toFixed(2) + " m";
  const lay = spec.layout || normalizeLayout(null, W, H, spec.sashes, spec.product, true).layout;
  const parts = lay.sections.map((s, i) => {
    const pos = lay.split === "columns" ? (i === 0 ? "left" : i === lay.sections.length - 1 ? "right" : "middle") : i === 0 ? "top" : i === lay.sections.length - 1 ? "bottom" : "middle";
    const cells = s.cells
      .map((c, j) => {
        const where = s.cells.length === 1 ? "" : lay.split === "columns" ? (j === 0 ? "upper " : j === s.cells.length - 1 ? "lower " : "middle ") : j === 0 ? "left " : "right ";
        const what = c.door ? "glazed door leaf" : OPENING_TEXT[c.opening] || "fixed glazing";
        return `${where}${m(c.size_mm)} ${what}`;
      })
      .join(" and ");
    return `${pos} ${m(s.size_mm)} ${lay.split === "columns" ? "wide column" : "high row"}: ${cells}`;
  });
  const kind = { door: "aluminium entrance door with glazed panels", facade: "aluminium curtain-wall facade glazing", balcony_block: "aluminium balcony door and window block", partition: "aluminium glazed partition" }[spec.product] || "aluminium window";
  const frame = colourText(spec.color);
  return (
    `Professional architectural product photo of a finished ${kind}, ${m(W)} wide by ${m(H)} high, slim thermally broken aluminium profiles, ${frame} frame. ` +
    `Layout: ${parts.join("; ")}. Exact proportions as described, mullions and transoms exactly where described. ` +
    `Front view, centered, straight-on, evenly lit, clean neutral studio background, realistic glass reflections, modern aluminium handles, catalog photography, no text, no people, no watermark.`
  );
}

async function render(env, spec) {
  if (!spec) throw new Error("spec is required");
  const out = await env.AI.run(IMAGE_MODEL, { prompt: buildRenderPrompt(spec), steps: 4 });
  if (!out || !out.image) throw new Error("image model returned nothing");
  return out.image;
}

function b64ToBytes(b64) {
  const clean = String(b64).replace(/^data:[^,]+,/, "");
  const bin = atob(clean);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}
