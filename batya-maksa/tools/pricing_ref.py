#!/usr/bin/env python3
"""Эталонный расчёт «Проём» (та же логика, что в iOS и Android).

  python3 tools/pricing_ref.py            — пересчитать catalog/test-cases.json (поле expected)
  python3 tools/pricing_ref.py --check    — сверить без перезаписи

iOS (XCTest) и Android (JUnit) читают test-cases.json и обязаны получить те же суммы.
"""
import json
import math
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = json.loads((ROOT / "catalog" / "catalog.json").read_text(encoding="utf-8"))
CASES_PATH = ROOT / "catalog" / "test-cases.json"


def cells_of(spec):
    """Ячейки сетки: (w, h, cell). split=columns: секции слева направо, ячейки сверху вниз; rows — наоборот."""
    W, H = spec["width_mm"], spec["height_mm"]
    lay = spec["layout"]
    out = []
    for sec in lay["sections"]:
        for cell in sec["cells"]:
            if lay["split"] == "columns":
                out.append((sec["size_mm"], cell["size_mm"], cell))
            else:
                out.append((cell["size_mm"], sec["size_mm"], cell))
    return out


def geometry(spec, system):
    W, H = spec["width_mm"], spec["height_mm"]
    lay = spec["layout"]
    secs = lay["sections"]
    g = {k: 0.0 for k in ["frame_perimeter_m", "impost_length_m", "imposts", "sash_perimeter_m", "sashes",
                          "door_perimeter_m", "doors", "glass_perimeter_m", "glass_area_m2", "panes",
                          "door_width_m", "vert_len_m", "horiz_len_m", "grid_len_m", "area_m2", "frame_corners"]}
    g["frame_perimeter_m"] = 2 * (W + H) / 1000
    g["area_m2"] = W * H / 1e6
    g["frame_corners"] = 4
    vert = 2 * H
    horiz = 2 * W
    imp_len = 0.0
    imp_n = 0
    cols = lay["split"] == "columns"
    # разделители между секциями
    for _ in secs[1:]:
        imp_n += 1
        if cols:
            imp_len += H; vert += H
        else:
            imp_len += W; horiz += W
    # разделители внутри секций
    for sec in secs:
        for _ in sec["cells"][1:]:
            imp_n += 1
            imp_len += sec["size_mm"]
            if cols:
                horiz += sec["size_mm"]
            else:
                vert += sec["size_mm"]
    g["impost_length_m"] = imp_len / 1000
    g["imposts"] = imp_n
    g["vert_len_m"] = vert / 1000
    g["horiz_len_m"] = horiz / 1000
    g["grid_len_m"] = (vert + horiz) / 1000
    for w, h, cell in cells_of(spec):
        opening = cell.get("opening", "fixed")
        door = bool(cell.get("door")) and opening != "fixed"
        if door:
            g["door_perimeter_m"] += 2 * (w + h) / 1000
            g["doors"] += 1
            g["door_width_m"] += w / 1000
            ded = system["deduction_door_mm"]
        elif opening != "fixed":
            g["sash_perimeter_m"] += 2 * (w + h) / 1000
            g["sashes"] += 1
            ded = system["deduction_sash_mm"]
        else:
            ded = system["deduction_fixed_mm"]
        gw, gh = max(w - ded, 100), max(h - ded, 100)
        g["glass_area_m2"] += gw * gh / 1e6
        g["glass_perimeter_m"] += 2 * (gw + gh) / 1000
        g["panes"] += 1
    return g


def round100(x):
    return math.floor(x / 100 + 0.5) * 100


def calculate(spec, sel, cat=CATALOG):
    arts = {a["code"]: a for a in cat["articles"]}
    system = next(s for s in cat["systems"] if s["id"] == sel["system_id"])
    g = geometry(spec, system)
    fill = sel["fill_mm"]
    door_product = spec.get("product") == "door"
    waste = 1 + cat["waste_percent"] / 100
    lines = []
    profiles = 0.0
    for r in system["rules"]:
        code = r.get("code")
        if door_product and r.get("door_code"):
            code = r["door_code"]
        if r.get("by_fill"):
            code = next((x["code"] for x in r["by_fill"] if x["min"] <= fill <= x["max"]), None)
        qty = g[r["base"]] * r["qty"]
        if qty <= 0 or code is None:
            continue
        a = arts[code]
        if r["profile"] and a["unit"] == "m":
            qty *= waste
        amount = qty * a["price"]
        if r["profile"]:
            profiles += amount
        lines.append(("profile" if r["profile"] else a["kind"], r["role"], code, qty, a["price"], amount))
    groups = {x["id"]: x["surcharge_percent"] for x in cat["color_groups"]}
    colors = {c["id"]: c for c in cat["colors"]}
    pct = 0.0
    if sel.get("color_id") in colors:
        pct += groups.get(colors[sel["color_id"]]["group"], 0)
    inside = sel.get("color_inside_id") or ""
    if inside and inside != sel.get("color_id"):
        pct += cat["two_color_percent"]
    if pct > 0:
        lines.append(("color", "Цвет", "", pct, 0, profiles * pct / 100))
    glass = sorted(cat["glass"], key=lambda x: x["fill_mm"])
    gi = next((x for x in glass if x["fill_mm"] >= fill), glass[-1])
    lines.append(("glass", gi["name"], "", g["glass_area_m2"], gi["price_per_m2"], g["glass_area_m2"] * gi["price_per_m2"]))
    hw = {h["opening"]: h for h in cat["hardware"]}
    counts = {}
    for _w, _h, cell in cells_of(spec):
        op = cell.get("opening", "fixed")
        if op == "fixed":
            continue
        key = "door" if cell.get("door") else op
        counts[key] = counts.get(key, 0) + 1
    for key in ["tilt_turn", "turn", "tilt", "top_hung", "slide", "door"]:
        if counts.get(key) and key in hw:
            lines.append(("hardware", hw[key]["name"], "", counts[key], hw[key]["price"], counts[key] * hw[key]["price"]))
    lines.append(("labor", "Изготовление", "", g["area_m2"], cat["labor_per_m2"], g["area_m2"] * cat["labor_per_m2"]))
    if sel.get("installation"):
        lines.append(("install", "Монтаж", "", g["area_m2"], cat["install_per_m2"], max(cat["install_min"], g["area_m2"] * cat["install_per_m2"])))
    subtotal = sum(l[5] for l in lines)
    markup = subtotal * cat["markup_percent"] / 100
    vat = (subtotal + markup) * cat["vat_percent"] / 100
    unit_total = round100(subtotal + markup + vat)
    q = max(1, int(spec.get("quantity", 1)))
    return {"subtotal": round(subtotal, 2), "unit_total": unit_total, "total": unit_total * q,
            "lines": len(lines), "profiles": round(profiles, 2)}, lines, g


def main():
    cases = json.loads(CASES_PATH.read_text(encoding="utf-8"))
    check = "--check" in sys.argv
    ok = True
    for c in cases:
        res, lines, g = calculate(c["spec"], c["selection"])
        if check:
            e = c["expected"]
            same = e["unit_total"] == res["unit_total"] and e["total"] == res["total"] and abs(e["subtotal"] - res["subtotal"]) < 0.5
            ok &= same
            print(("OK  " if same else "BAD ") + c["name"], res)
        else:
            c["expected"] = res
            print(c["name"], res)
            for l in lines:
                print("   %-9s %-38s %-22s %8.2f × %9.2f = %11.2f" % l)
            print("   geometry:", {k: round(v, 3) for k, v in g.items() if v})
    if not check:
        text = json.dumps(cases, ensure_ascii=False, indent=1)
        for p in [CASES_PATH, ROOT / "ios" / "OknoCalcTests" / "test-cases.json",
                  ROOT / "android" / "app" / "src" / "test" / "resources" / "test-cases.json"]:
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(text, encoding="utf-8")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
