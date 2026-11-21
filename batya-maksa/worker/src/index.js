// Cloudflare Worker: ИИ-бэкенд приложения OknoCalc.
// POST /analyze  {image: base64 jpeg}            -> {spec}
// POST /render   {spec, image?: base64 jpeg}     -> {image: base64 jpeg/png}
// Без токена: защита — лимит запросов с IP и ограничение размера изображения.

const VISION_MODELS = [
  "@cf/meta/llama-4-scout-17b-16e-instruct",
  "@cf/mistralai/mistral-small-3.1-24b-instruct",
  "@cf/google/gemma-3-12b-it",
];
const LEGACY_VISION_MODEL = "@cf/meta/llama-3.2-11b-vision-instruct";
const IMAGE_MODEL = "@cf/black-forest-labs/flux-1-schnell";

const WINDOW_TYPES = [
  "fixed", // глухое
  "single_sash", // одностворчатое
  "double_sash", // двухстворчатое
  "triple_sash", // трёхстворчатое
  "balcony_door", // балконная дверь
  "balcony_block", // балконный блок (дверь + окно)
  "sliding", // раздвижное
  "entrance_door", // входная дверь
  "interior_door", // межкомнатная дверь
];
const OPENINGS = ["fixed", "tilt", "turn", "tilt_turn", "slide"];

// Нативному приложению CORS не нужен; браузерам доступ не открываем.
const CORS = {};

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);

    // Защита от злоупотреблений: лимит запросов с одного IP (если биндинг LIMITER подключён).
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
      if (path === "/analyze") return json({ spec: await analyze(env, body.image) });
      if (path === "/render") return json({ image: await render(env, body.spec) });
      return json({ error: "not_found" }, 404);
    } catch (e) {
      return json({ error: "ai_failed", message: String(e && e.message ? e.message : e) }, 502);
    }
  },
};

// ---------- /analyze ----------

const ANALYZE_PROMPT = `You are an expert in window and door manufacturing. The image is a technical drawing (sketch) of a window or door, usually with dimensions and notes written next to it.
Read the drawing and return ONLY a JSON object, no markdown, with these keys:
{
 "numbers": [string] (FIRST copy every dimension number exactly as written on the drawing, digit by digit, e.g. ["1800","1400","900"]),
 "width_mm": number (overall width in millimetres; convert cm/m to mm),
 "height_mm": number (overall height in millimetres),
 "type": one of ${JSON.stringify(WINDOW_TYPES)},
 "sashes": [ {"width_mm": number or null, "opening": one of ${JSON.stringify(OPENINGS)}} ] (one item per section/sash, left to right),
 "profile": string (profile system or material written on the drawing, e.g. "PVC 5-chamber", "aluminium", "wood"; empty string if not written),
 "glass": string (e.g. "single", "double-glazed 24mm", "triple"; empty string if not written),
 "color": string (frame colour if written, else "white"),
 "extras": [string] (every extra item written: sill/подоконник, ebb/отлив, slopes/откосы, mosquito net/сетка, etc., in the original language),
 "notes": string (ALL words written on the drawing, copied verbatim in the original language, e.g. "Профиль Rehau 70 мм, ламинация дуб, отлив, откосы"),
 "confidence": number from 0 to 1
}
How to read sashes: vertical/horizontal lines inside the frame (imposts) split it into sections. Diagonal lines inside a section show how it opens: lines meeting at the left or right side = "turn" (side-hung), lines meeting at the bottom center = "tilt", both patterns in one section = "tilt_turn", no diagonals = "fixed", arrows along the frame = "slide".
The photo may be rotated, skewed or in shadow: read the numbers as written, the bigger horizontal dimension line is the width.
"type" must match the number of sections (1 = single_sash or fixed, 2 = double_sash, 3 = triple_sash) unless it is clearly a door or balcony block.
Width and height MUST be taken from "numbers": the overall dimension lines go along the whole frame. Section widths should add up to the overall width. Handwritten digits: 1 vs 7, 3 vs 8, 5 vs 6 — check carefully.
If a value is not visible, make the most reasonable guess and lower "confidence". Output JSON only.`;

const SPEC_SCHEMA = {
  type: "object",
  properties: {
    numbers: { type: "array", items: { type: "string" } },
    width_mm: { type: "number" },
    height_mm: { type: "number" },
    type: { type: "string", enum: WINDOW_TYPES },
    sashes: {
      type: "array",
      items: {
        type: "object",
        properties: { width_mm: { type: ["number", "null"] }, opening: { type: "string", enum: OPENINGS } },
        required: ["opening"],
      },
    },
    profile: { type: "string" },
    glass: { type: "string" },
    color: { type: "string" },
    extras: { type: "array", items: { type: "string" } },
    notes: { type: "string" },
    confidence: { type: "number" },
  },
  required: ["numbers", "width_mm", "height_mm", "type", "sashes"],
};

const responseText = (res) =>
  typeof res === "string" ? res : (res && (res.response ?? res.result?.response ?? res.description ?? res.choices?.[0]?.message?.content)) || "";

async function tryModel(env, model, dataUri, withSchema) {
  const input = {
    messages: [
      { role: "system", content: "You read window/door technical drawings and reply with JSON only." },
      {
        role: "user",
        content: [
          { type: "text", text: ANALYZE_PROMPT },
          { type: "image_url", image_url: { url: dataUri } },
        ],
      },
    ],
    max_tokens: 600,
    temperature: 0.1,
  };
  if (withSchema) input.response_format = { type: "json_schema", json_schema: SPEC_SCHEMA };
  const res = await env.AI.run(model, input);
  const out = res && typeof res.response === "object" && res.response !== null ? res.response : responseText(res);
  return extractJson(out);
}

async function tryLegacy(env, bytes) {
  const run = () =>
    env.AI.run(LEGACY_VISION_MODEL, { image: [...bytes], prompt: ANALYZE_PROMPT, max_tokens: 700, temperature: 0.1 });
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

  // Две модели параллельно: сверяем цифры. Вторую ждём не дольше 5 с после первой.
  const [m1, m2] = VISION_MODELS;
  const p1 = withTimeout(runModel(env, m1, dataUri), 30000).then((r) => ({ r, m: m1 }));
  const p2 = withTimeout(runModel(env, m2, dataUri), 30000).then((r) => ({ r, m: m2 }));
  const results = [];
  const first = await Promise.any([p1, p2]).catch(() => null);
  if (first) {
    results.push(first);
    const other = first.m === m1 ? p2 : p1;
    const second = await withTimeout(other, 5000).catch(() => null);
    if (second) results.push(second);
  }

  if (results.length === 0) {
    for (const m of VISION_MODELS.slice(2)) {
      try {
        results.push({ r: await withTimeout(runModel(env, m, dataUri), 30000), m });
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
    return {
      ...normalizeSpec({}),
      confidence: 0,
      notes: "ИИ не смог прочитать чертёж. Проверьте размеры и параметры вручную.",
      model: "none",
      ms: Date.now() - started,
    };
  }
  const specs = results.map(({ r, m }) => ({ ...normalizeSpec(r), model: m }));
  return { ...reconcile(specs), ms: Date.now() - started };
}

/** Насколько спецификация сама с собой согласована (0..3). */
export function selfScore(s) {
  let score = 0;
  const widths = s.sashes.map((x) => x.width_mm).filter(Boolean);
  if (widths.length === s.sashes.length && widths.length > 1) {
    const sum = widths.reduce((a, b) => a + b, 0);
    if (Math.abs(sum - s.width_mm) / s.width_mm < 0.04) score += 1;
  }
  const nums = (s.numbers || []).map(Number);
  if (nums.includes(s.width_mm)) score += 1;
  if (nums.includes(s.height_mm)) score += 1;
  return score;
}

const close = (a, b) => Math.abs(a - b) / Math.max(a, b) < 0.03;

/** Сверка ответов моделей: согласие повышает уверенность, расхождение — варианты на выбор. */
export function reconcile(specs) {
  const ranked = [...specs].sort((a, b) => selfScore(b) - selfScore(a) || b.confidence - a.confidence);
  const best = { ...ranked[0] };
  delete best.numbers;
  if (ranked.length < 2) {
    best.confidence = Math.min(best.confidence, selfScore(ranked[0]) >= 2 ? 0.85 : 0.6);
    return best;
  }
  const other = ranked[1];
  const wOk = close(best.width_mm, other.width_mm);
  const hOk = close(best.height_mm, other.height_mm);
  if (wOk && hOk) {
    best.confidence = Math.max(best.confidence, 0.9);
    if (best.sashes.length !== other.sashes.length) best.confidence = 0.75;
    return best;
  }
  best.confidence = Math.min(best.confidence, 0.5);
  best.alternatives = {
    width_mm: wOk ? [] : [other.width_mm],
    height_mm: hOk ? [] : [other.height_mm],
  };
  return best;
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

export function normalizeSpec(raw) {
  raw = raw || {};
  // Единицы: если оба размера < 400, значит в сантиметрах; < 10 — в метрах.
  let w = Number(raw.width_mm), h = Number(raw.height_mm), k = 1;
  if (w > 0 && h > 0) {
    if (w < 10 && h < 10) k = 1000;
    else if (w < 400 && h < 400) k = 10;
  }
  if (k !== 1) {
    raw = { ...raw, width_mm: w * k, height_mm: h * k,
      sashes: (raw.sashes || []).map((x) => ({ ...x, width_mm: x && x.width_mm ? x.width_mm * k : null })) };
  }
  const num = (v, min, max, fallback) => {
    const n = Number(v);
    if (!Number.isFinite(n) || n <= 0) return fallback;
    return Math.min(max, Math.max(min, Math.round(n)));
  };
  const type = WINDOW_TYPES.includes(raw.type) ? raw.type : "single_sash";
  const sashes = (Array.isArray(raw.sashes) ? raw.sashes : [])
    .slice(0, 6)
    .map((s) => ({
      width_mm: s && s.width_mm ? num(s.width_mm, 100, 5000, null) : null,
      opening: OPENINGS.includes(s && s.opening) ? s.opening : "fixed",
    }));
  if (sashes.length === 0) sashes.push({ width_mm: null, opening: "tilt_turn" });
  const widthMm = num(raw.width_mm, 200, 6000, 1300);
  // Ширины створок: если сумма не сходится с общей шириной — делим поровну.
  const given = sashes.map((x) => x.width_mm);
  const sum = given.reduce((a, b) => a + (b || 0), 0);
  if (given.some((x) => !x) || Math.abs(sum - widthMm) / widthMm > 0.06) {
    const missing = given.filter((x) => !x).length;
    const known = given.reduce((a, b) => a + (b || 0), 0);
    if (missing > 0 && known < widthMm && known > 0) {
      const each = Math.round((widthMm - known) / missing);
      sashes.forEach((x) => { if (!x.width_mm) x.width_mm = each; });
    } else {
      sashes.forEach((x) => { x.width_mm = Math.round(widthMm / sashes.length); });
    }
  }

  // Тип окна согласуем с количеством секций (кроме дверей и балконных блоков).
  let fixedType = type;
  const windowTypes = ["fixed", "single_sash", "double_sash", "triple_sash"];
  if (windowTypes.includes(type)) {
    const n = sashes.length;
    const allFixed = sashes.every((x) => x.opening === "fixed");
    fixedType = n >= 3 ? "triple_sash" : n === 2 ? "double_sash" : allFixed ? "fixed" : "single_sash";
  }

  return {
    numbers: (Array.isArray(raw.numbers) ? raw.numbers : []).map(String).slice(0, 20),
    width_mm: widthMm,
    height_mm: num(raw.height_mm, 200, 3500, 1400),
    type: fixedType,
    sashes,
    profile: String(raw.profile || "").slice(0, 80),
    glass: String(raw.glass || "").slice(0, 80),
    color: String(raw.color || "white").slice(0, 40),
    extras: (Array.isArray(raw.extras) ? raw.extras : []).map(String).slice(0, 10),
    notes: String(raw.notes || "").slice(0, 500),
    confidence: Math.min(1, Math.max(0, Number(raw.confidence) || 0.5)),
  };
}

// ---------- /render ----------

const OPENING_TEXT = {
  fixed: "fixed pane",
  tilt: "tilt-only sash",
  turn: "side-hung opening sash",
  tilt_turn: "tilt-and-turn sash",
  slide: "sliding sash",
};

export function buildRenderPrompt(spec) {
  const w = (spec.width_mm / 1000).toFixed(2);
  const h = (spec.height_mm / 1000).toFixed(2);
  const ratio = spec.width_mm / spec.height_mm;
  const shape = ratio > 1.15 ? "wide landscape" : ratio < 0.85 ? "tall portrait" : "nearly square";
  const sashes = spec.sashes
    .map((s, i) => `section ${i + 1}: ${OPENING_TEXT[s.opening] || "fixed pane"}`)
    .join(", ");
  const material = /alu/i.test(spec.profile)
    ? "aluminium"
    : /wood|дерев/i.test(spec.profile)
    ? "wooden"
    : "white PVC (uPVC)";
  const color = spec.color && !/white|бел/i.test(spec.color) ? `${spec.color} colour frame` : "white frame";
  const glass = /triple|тр[её]х/i.test(spec.glass) ? "triple glazing" : "double glazing";
  const extras = spec.extras && spec.extras.length ? `, with ${spec.extras.join(", ")}` : "";
  return (
    `Professional product photo of a finished ${material} ${spec.type.replace(/_/g, " ")} ` +
    `${shape} window, ${w} m wide by ${h} m high, ${sashes}, ${color}, ${glass}, ` +
    `modern hardware handle${extras}. Front view, centered, straight-on, evenly lit, ` +
    `clean neutral studio background, sharp details, realistic reflections in the glass, ` +
    `catalog photography, no text, no watermark.`
  );
}

async function render(env, spec) {
  if (!spec) throw new Error("spec is required");
  const out = await env.AI.run(IMAGE_MODEL, {
    prompt: buildRenderPrompt(spec),
    steps: 4,
  });
  if (!out || !out.image) throw new Error("image model returned nothing");
  return out.image; // base64 JPEG
}

function b64ToBytes(b64) {
  const clean = String(b64).replace(/^data:[^,]+,/, "");
  const bin = atob(clean);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}
