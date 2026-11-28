#!/usr/bin/env python3
"""Эталонный расчёт «Проём» v3 (та же логика, что в iOS и Android).

КП (коммерческое предложение) — несколько окон. Считается так же, как ORGADATA/Logikal:
  * геометрия по вычетам системы: рама по наружному размеру (45°), импосты между профилями,
    створка = проём + нахлёст, штапики и стёкла от проёма;
  * раскрой: детали всех окон КП одного артикула и цвета раскладываются по хлыстам
    (первый подходящий, от длинных к коротким; пропил и обрезка концов) — платим за целые хлысты;
  * уплотнители на весь КП округляются вверх кратно gasket_step_m;
  * режим «Материалы» — профили, комплектующие, уплотнители по прайсу (цены с НДС), без наценки;
    режим «Под ключ» — плюс стеклопакеты, фурнитура, работа, монтаж и наценка. НДС в цене («в т. ч.»).

  python3 tools/pricing_ref.py            — пересчитать test-cases.json (поле expected)
  python3 tools/pricing_ref.py --check    — сверить без перезаписи
"""
import json
import math
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = json.loads((ROOT / "catalog" / "catalog.json").read_text(encoding="utf-8"))
CASES_PATH = ROOT / "catalog" / "test-cases.json"

GEO_DEFAULT = {"frame_face": 42, "impost_face": 42, "sash_overlap": 6, "sash_face": 59, "door_face": 73,
               "bead_deduct": 22, "glass_gap": 6}
BASES = ["frame_perimeter_m", "impost_length_m", "imposts", "sash_perimeter_m", "sashes", "door_perimeter_m", "doors",
         "door_width_m", "bead_length_m", "glass_perimeter_m", "glass_area_m2", "panes", "sash_panes",
         "vert_len_m", "horiz_len_m", "grid_len_m", "area_m2", "frame_corners"]
# Какие величины раскладываются на детали (для раскроя)
PIECES_OF = {"frame_perimeter_m": "frame", "impost_length_m": "impost", "sash_perimeter_m": "sash",
             "door_perimeter_m": "door", "door_width_m": "threshold", "bead_length_m": "bead",
             "vert_len_m": "vert", "horiz_len_m": "horiz", "grid_len_m": "grid"}


def round0(x):
    return math.floor(x + 0.5)


# ---------------- Геометрия ----------------

def geometry(spec, system):
    """Детали (мм) и величины изделия. Все длины — целые миллиметры, как в карте раскроя."""
    geo = {**GEO_DEFAULT, **(system.get("geo") or {})}
    F, I2 = geo["frame_face"], geo["impost_face"] / 2
    ov, sf, df = geo["sash_overlap"], geo["sash_face"], geo["door_face"]
    bd, gap = geo["bead_deduct"], geo["glass_gap"]
    W, H = int(spec["width_mm"]), int(spec["height_mm"])
    lay = spec["layout"]
    cols = lay["split"] == "columns"
    secs = lay["sections"]
    main_total, cross_total = (W, H) if cols else (H, W)
    door_product = spec.get("product") == "door"

    p = {k: [] for k in ["frame", "impost", "sash", "door", "threshold", "bead", "vert", "horiz", "grid"]}
    g = {k: 0.0 for k in BASES}
    p["frame"] = [W, H, H] if door_product else [W, W, H, H]
    g["frame_corners"] = 4
    g["area_m2"] = W * H / 1e6
    cells = []          # (light_w, light_h, cell, section, index, nominal_w, nominal_h)
    n = len(secs)
    for si, sec in enumerate(secs):
        s_main = int(sec["size_mm"])
        a = F if si == 0 else I2
        b = F if si == n - 1 else I2
        light_main = s_main - a - b
        if si > 0:
            p["impost"].append(int(round0(cross_total - 2 * F)))
        m = len(sec["cells"])
        for ci, cell in enumerate(sec["cells"]):
            c_cross = int(cell["size_mm"])
            c1 = F if ci == 0 else I2
            c2 = F if ci == m - 1 else I2
            light_cross = c_cross - c1 - c2
            if ci > 0:
                p["impost"].append(int(round0(light_main)))
            lw, lh = (light_main, light_cross) if cols else (light_cross, light_main)
            nw, nh = (s_main, c_cross) if cols else (c_cross, s_main)
            cells.append((int(round0(lw)), int(round0(lh)), cell, si, ci, nw, nh))
    # Фасад и стоечно-ригельные: номинальные стойки и ригели
    if cols:
        p["vert"] = [H, H] + [H] * (n - 1)
        p["horiz"] = [W, W] + [int(s["size_mm"]) for s in secs for _ in s["cells"][1:]]
    else:
        p["horiz"] = [W, W] + [W] * (n - 1)
        p["vert"] = [H, H] + [int(s["size_mm"]) for s in secs for _ in s["cells"][1:]]
    p["grid"] = p["vert"] + p["horiz"]

    panes = []          # (glass_w, glass_h, in_sash)
    sash_list = []      # (outer_w, outer_h, glass_w, glass_h, cell)
    for lw, lh, cell, si, ci, nw, nh in cells:
        op = cell.get("opening", "fixed")
        door = bool(cell.get("door")) and op != "fixed"
        if op == "fixed":
            gl_w, gl_h = lw, lh
            in_sash = False
        else:
            ow, oh = lw + 2 * ov, lh + 2 * ov
            face = df if door else sf
            gl_w, gl_h = ow - 2 * face, oh - 2 * face
            if door:
                p["door"] += [ow, ow, oh, oh]
                p["threshold"].append(lw)
                g["doors"] += 1
            else:
                p["sash"] += [ow, ow, oh, oh]
                g["sashes"] += 1
            sash_list.append((ow, oh, gl_w - 2 * gap, gl_h - 2 * gap, cell, si, ci, door))
            in_sash = True
        p["bead"] += [gl_w, gl_w, gl_h - 2 * bd, gl_h - 2 * bd]
        gw, gh = max(gl_w - 2 * gap, 50), max(gl_h - 2 * gap, 50)
        panes.append((gw, gh, in_sash))
    g["imposts"] = len(p["impost"])
    for k, base in [("frame", "frame_perimeter_m"), ("impost", "impost_length_m"), ("sash", "sash_perimeter_m"),
                    ("door", "door_perimeter_m"), ("threshold", "door_width_m"), ("bead", "bead_length_m"),
                    ("vert", "vert_len_m"), ("horiz", "horiz_len_m"), ("grid", "grid_len_m")]:
        g[base] = sum(p[k]) / 1000
    for gw, gh, in_sash in panes:
        g["glass_area_m2"] += gw * gh / 1e6
        g["glass_perimeter_m"] += 2 * (gw + gh) / 1000
        g["panes"] += 1
        if in_sash:
            g["sash_panes"] += 1
    return g, p, sash_list


# ---------------- Каталог ----------------

def article(cat, code):
    for a in cat["articles"]:
        if a["code"] == code:
            return a
    return None


def bar_length(cat, code):
    """Длина хлыста: самое длинное совпадение начала артикула (точный артикул — тоже совпадение), иначе по умолчанию."""
    best = None
    for b in cat.get("bar_lengths") or []:
        if code.startswith(b["prefix"]) and (best is None or len(b["prefix"]) > len(best["prefix"])):
            best = b
    return best["mm"] if best else cat.get("bar_default_mm", 6500)


def rule_code(r, spec, sel):
    code = r.get("code")
    if spec.get("product") == "door" and r.get("door_code"):
        code = r["door_code"]
    if r.get("by_fill"):
        code = next((x["code"] for x in r["by_fill"] if x["min"] <= sel["fill_mm"] <= x["max"]), None)
    ov = next((o["code"] for o in sel.get("overrides") or [] if o["role"] == r["role"]), None)
    return ov or code


def glass_item(cat, fill):
    glass = sorted(cat["glass"], key=lambda x: x["fill_mm"])
    return next((x for x in glass if x["fill_mm"] >= fill), glass[-1])


# ---------------- Проверка стандартных размеров ----------------

def check_limits(spec, sel, system, cat, p, sash_list):
    """Предупреждения о нестандартных размерах и рекомендации (код, роль). Считаем всё равно стандартными профилями."""
    out = []
    lim = system.get("limits") or {}
    rec = {r["when"]: r for r in system.get("recommend") or []}
    overrides = {o["role"]: o["code"] for o in sel.get("overrides") or []}
    kerf, trim = cat.get("saw_kerf_mm", 10), cat.get("bar_trim_mm", 20)

    def add(kind, text):
        r = rec.get(kind)
        applied = bool(r and overrides.get(r["role"]) == r["code"])
        out.append({"kind": kind, "text": text, "role": r["role"] if r else "", "code": r["code"] if r else "",
                    "rec": r["text"] if r else "", "applied": applied})

    frame_code = next((rule_code(r, spec, sel) for r in system["rules"] if r["base"] == "frame_perimeter_m"), None)
    if frame_code and p["frame"]:
        usable = bar_length(cat, frame_code) - trim - kerf
        longest = max(p["frame"])
        if longest > usable:
            add("frame_long", f"Сторона рамы {longest} мм длиннее хлыста ({usable} мм) — нужна стыковка, нестандарт")
    if lim.get("impost_max_mm") and p["impost"] and max(p["impost"]) > lim["impost_max_mm"]:
        add("impost_long", f"Импост {max(p['impost'])} мм длиннее стандартного ({lim['impost_max_mm']} мм)")
    gi = glass_item(cat, sel["fill_mm"])
    sash_kg = 0
    for ow, oh, gw, gh, cell, si, ci, door in sash_list:
        name = f"{'Дверь' if door else 'Створка'} {si + 1}.{ci + 1} ({ow}×{oh} мм)"
        if door:
            if (lim.get("door_max_w") and ow > lim["door_max_w"]) or (lim.get("door_max_h") and oh > lim["door_max_h"]):
                add("door_big", f"{name} больше стандартной двери ({lim['door_max_w']}×{lim['door_max_h']} мм)")
            continue
        kg = gw * gh / 1e6 * gi.get("kg_m2", 25) + 2 * (ow + oh) / 1000 * 1.65
        sash_kg = max(sash_kg, kg)
        if not lim:
            continue
        if ow > lim.get("sash_max_w", 1e9) or oh > lim.get("sash_max_h", 1e9) or kg > lim.get("sash_max_kg", 1e9):
            add("sash_big", f"{name}, ≈{kg:.0f} кг — больше стандартной ({lim['sash_max_w']}×{lim['sash_max_h']} мм, до {lim['sash_max_kg']} кг)")
        elif ow < lim.get("sash_min_w", 0) or oh < lim.get("sash_min_h", 0):
            add("sash_small", f"{name} меньше минимальной для фурнитуры ({lim['sash_min_w']}×{lim['sash_min_h']} мм)")
    return out


# ---------------- Потребность одного окна ----------------

def item_needs(spec, sel, cat, scope):
    system = next(s for s in cat["systems"] if s["id"] == sel["system_id"])
    g, p, sash_list = geometry(spec, system)
    needs = []
    color_key = (sel.get("color_id") or "") + "/" + (sel.get("color_inside_id") or "")
    for r in system["rules"]:
        code = rule_code(r, spec, sel)
        qty = g[r["base"]] * r["qty"] + sum(g[x["base"]] * x["qty"] for x in r.get("plus") or [])
        if qty <= 0 or code is None:
            continue
        a = article(cat, code)
        if a is None:
            continue
        group = "profile" if r["profile"] else ("gasket" if a["kind"] == "gasket" else "accessory")
        pieces = None
        if group == "profile" and a["unit"] == "m" and not r.get("plus") and r["qty"] == 1 and r["base"] in PIECES_OF:
            pieces = p[PIECES_OF[r["base"]]]
        needs.append({"group": group, "role": r["role"], "code": code, "unit": a["unit"], "qty": qty, "price": a["price"],
                      "pieces": pieces, "ckey": color_key if group == "profile" else ""})
    if scope == "turnkey":
        gi = glass_item(cat, sel["fill_mm"])
        needs.append({"group": "glass", "role": gi["name"], "code": "", "unit": "m2", "qty": g["glass_area_m2"],
                      "price": gi["price_per_m2"], "pieces": None, "ckey": ""})
        hw = {h["opening"]: h for h in cat["hardware"]}
        counts = {}
        for sec in spec["layout"]["sections"]:
            for cell in sec["cells"]:
                op = cell.get("opening", "fixed")
                if op != "fixed":
                    key = "door" if cell.get("door") else op
                    counts[key] = counts.get(key, 0) + 1
        for key in ["tilt_turn", "turn", "tilt", "top_hung", "slide", "door"]:
            if counts.get(key) and key in hw:
                needs.append({"group": "hardware", "role": hw[key]["name"], "code": "", "unit": "pc", "qty": counts[key],
                              "price": hw[key]["price"], "pieces": None, "ckey": ""})
        needs.append({"group": "labor", "role": "Изготовление", "code": "", "unit": "m2", "qty": g["area_m2"],
                      "price": cat["labor_per_m2"], "pieces": None, "ckey": ""})
        if sel.get("installation"):
            amount = max(cat["install_min"], g["area_m2"] * cat["install_per_m2"])
            needs.append({"group": "install", "role": "Монтаж", "code": "", "unit": "m2", "qty": g["area_m2"],
                          "price": amount / g["area_m2"] if g["area_m2"] else 0, "pieces": None, "ckey": ""})
    warnings = check_limits(spec, sel, system, cat, p, sash_list)
    return needs, g, warnings


# ---------------- Раскрой ----------------

def cut_bars(pieces, bar_mm, kerf, trim):
    """Первый подходящий хлыст, детали от длинных к коротким. Возвращает (число хлыстов, деталей длиннее хлыста)."""
    cap = bar_mm - trim
    free = []
    over = 0
    extra = 0
    for L in sorted(pieces, reverse=True):
        need = L + kerf
        if need > cap:
            over += 1
            extra += math.ceil(need / cap)
            continue
        for i, f in enumerate(free):
            if f >= need:
                free[i] = f - need
                break
        else:
            free.append(cap - need)
    return len(free) + extra, over


# ---------------- КП ----------------

def calc_offer(offer, cat=CATALOG):
    opt = {"scope": "materials", "cutting": True, "gasket_round": True, "discount_percent": 0, **(offer.get("options") or {})}
    scope = opt["scope"]
    waste = 1 + cat["waste_percent"] / 100
    kerf, trim, step = cat.get("saw_kerf_mm", 10), cat.get("bar_trim_mm", 20), cat.get("gasket_step_m", 20)
    items = offer["items"]
    per = []
    for it in items:
        needs, g, warns = item_needs(it["spec"], it["selection"], cat, scope)
        per.append({"needs": needs, "g": g, "warnings": warns, "q": max(1, int(it["spec"].get("quantity", 1)))})

    # Пулы: профили по (артикул, цвет) — детали в хлысты; уплотнители по артикулу — кратно шагу
    pools = {}
    for i, x in enumerate(per):
        for n in x["needs"]:
            key = None
            if n["group"] == "profile" and opt["cutting"] and n["pieces"]:
                key = ("profile", n["code"], n["ckey"])
            elif n["group"] == "gasket" and opt["gasket_round"] and step > 0 and n["unit"] == "m":
                key = ("gasket", n["code"], "")
            if key:
                pool = pools.setdefault(key, {"need": 0.0, "pieces": [], "by": {}, "price": n["price"]})
                amount_m = n["qty"] * x["q"]
                pool["need"] += amount_m
                pool["by"][(i, n["role"])] = pool["by"].get((i, n["role"]), 0) + amount_m
                if key[0] == "profile":
                    pool["pieces"] += n["pieces"] * x["q"]
                n["pool"] = key
    materials = []
    for key, pool in pools.items():
        kind, code, ckey = key
        a = article(cat, code)
        if kind == "profile":
            bar = bar_length(cat, code)
            bars, over = cut_bars(pool["pieces"], bar, kerf, trim)
            bought = bars * bar / 1000
            pool.update(bought=bought, bars=bars, bar_mm=bar, over=over)
        else:
            bought = math.ceil(pool["need"] / step - 1e-9) * step
            pool.update(bought=bought)
        materials.append({"group": kind, "code": code, "name": a["name"], "ckey": ckey, "need": round(pool["need"], 3),
                          "bought": round(pool["bought"], 3), "bars": pool.get("bars"), "bar_mm": pool.get("bar_mm"),
                          "price": a["price"], "amount": round(pool["bought"] * a["price"], 2)})

    out_items = []
    for i, x in enumerate(per):
        lines = []
        profiles = 0.0
        for n in x["needs"]:
            if "pool" in n:
                pool = pools[n["pool"]]
                share = pool["by"][(i, n["role"])] / pool["need"] if pool["need"] else 0
                qty = pool["bought"] * share / x["q"]
            elif n["group"] == "profile" and n["unit"] == "m":
                qty = n["qty"] * waste
            else:
                qty = n["qty"]
            amount = qty * n["price"]
            if n["group"] == "profile":
                profiles += amount
            lines.append((n["group"], n["role"], n["code"], qty, n["price"], amount))
        sel = items[i]["selection"]
        groups = {gr["id"]: gr["surcharge_percent"] for gr in cat["color_groups"]}
        colors = {c["id"]: c for c in cat["colors"]}
        pct = 0.0
        if sel.get("color_id") in colors:
            pct += groups.get(colors[sel["color_id"]]["group"], 0)
        inside = sel.get("color_inside_id") or ""
        if inside and inside != sel.get("color_id"):
            pct += cat["two_color_percent"]
        if pct > 0:
            lines.append(("color", "Цвет", "", pct, 0, profiles * pct / 100))
        subtotal = sum(l[5] for l in lines)
        markup = subtotal * cat["markup_percent"] / 100 if scope == "turnkey" else 0.0
        unit_total = round0(subtotal + markup)
        out_items.append({"unit_total": unit_total, "total": unit_total * x["q"], "quantity": x["q"],
                          "subtotal": round(subtotal, 2), "area_m2": round(x["g"]["area_m2"], 4),
                          "warnings": [w["kind"] for w in x["warnings"]], "lines": lines})
    subtotal = sum(it["total"] for it in out_items)
    discount = round0(subtotal * opt["discount_percent"] / 100)
    total = subtotal - discount
    vat = round0(total * cat["vat_percent"] / (100 + cat["vat_percent"]))
    area = sum(it["area_m2"] * it["quantity"] for it in out_items)
    bars = {}
    for m in materials:
        if m["group"] == "profile":
            bars[m["code"]] = bars.get(m["code"], 0) + m["bars"]
    return {"items": out_items, "subtotal": subtotal, "discount": discount, "total": total, "vat_included": vat,
            "area_m2": round(area, 4), "bars": bars, "materials": materials}


def summary(res):
    return {"unit_totals": [i["unit_total"] for i in res["items"]], "totals": [i["total"] for i in res["items"]],
            "subtotal": res["subtotal"], "discount": res["discount"], "total": res["total"],
            "vat_included": res["vat_included"], "bars": res["bars"],
            "warnings": [i["warnings"] for i in res["items"]]}


def main():
    cases = json.loads(CASES_PATH.read_text(encoding="utf-8"))
    check = "--check" in sys.argv
    ok = True
    for c in cases:
        res = calc_offer(c["offer"])
        s = summary(res)
        if check:
            same = s == c["expected"]
            ok &= same
            print(("OK  " if same else "BAD ") + c["name"], s if not same else "")
        else:
            c["expected"] = s
            print("==", c["name"], json.dumps(s, ensure_ascii=False))
            for it in res["items"]:
                for l in it["lines"]:
                    print("   %-9s %-42s %-24s %9.3f × %9.2f = %11.2f" % l)
            for m in res["materials"]:
                print("   mat %-8s %-24s need %8.3f bought %8.3f bars %s" % (m["group"], m["code"], m["need"], m["bought"], m["bars"]))
    if not check:
        text = json.dumps(cases, ensure_ascii=False, indent=1)
        for p in [CASES_PATH, ROOT / "ios" / "OknoCalcTests" / "test-cases.json",
                  ROOT / "android" / "app" / "src" / "test" / "resources" / "test-cases.json"]:
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(text, encoding="utf-8")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
