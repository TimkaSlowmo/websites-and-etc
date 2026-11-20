// Cloudflare Worker: ИИ-бэкенд приложения OknoCalc.
// POST /analyze  {image: base64 jpeg}            -> {spec}
// POST /render   {spec, image?: base64 jpeg}     -> {image: base64 jpeg/png}
// Без токена: защита — лимит запросов с IP и ограничение размера изображения.

const VISION_MODEL = "@cf/meta/llama-3.2-11b-vision-instruct";
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
 "width_mm": number (overall width in millimetres; convert cm/m to mm),
 "height_mm": number (overall height in millimetres),
 "type": one of ${JSON.stringify(WINDOW_TYPES)},
 "sashes": [ {"width_mm": number or null, "opening": one of ${JSON.stringify(OPENINGS)}} ] (one item per section/sash, left to right),
 "profile": string (profile system or material written on the drawing, e.g. "PVC 5-chamber", "aluminium", "wood"; empty string if not written),
 "glass": string (e.g. "single", "double-glazed 24mm", "triple"; empty string if not written),
 "color": string (frame colour if written, else "white"),
 "extras": [string] (sill, mosquito net, handles, slopes, etc. if written),
 "notes": string (any other text from the drawing, short),
 "confidence": number from 0 to 1
}
If a value is not visible, make the most reasonable guess and lower "confidence". Output JSON only.`;

async function analyze(env, imageB64) {
  if (!imageB64) throw new Error("image is required");
  const bytes = b64ToBytes(imageB64);
  if (bytes.length > 5 * 1024 * 1024) throw new Error("image too large");

  const run = () =>
    env.AI.run(VISION_MODEL, {
      image: [...bytes],
      prompt: ANALYZE_PROMPT,
      max_tokens: 700,
      temperature: 0.1,
    });

  let res;
  try {
    res = await run();
  } catch (e) {
    // Первый запуск модели Llama Vision требует принять лицензию Meta.
    if (/agree/i.test(String(e && e.message))) {
      await env.AI.run(VISION_MODEL, { prompt: "agree" });
      res = await run();
    } else {
      throw e;
    }
  }

  const text = typeof res === "string" ? res : res.response ?? res.description ?? "";
  return normalizeSpec(extractJson(text));
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

  return {
    width_mm: num(raw.width_mm, 200, 6000, 1300),
    height_mm: num(raw.height_mm, 200, 3500, 1400),
    type,
    sashes,
    profile: String(raw.profile || "").slice(0, 80),
    glass: String(raw.glass || "").slice(0, 80),
    color: String(raw.color || "white").slice(0, 40),
    extras: (Array.isArray(raw.extras) ? raw.extras : []).map(String).slice(0, 10),
    notes: String(raw.notes || "").slice(0, 300),
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
    steps: 6,
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
