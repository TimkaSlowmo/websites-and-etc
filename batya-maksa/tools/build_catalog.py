#!/usr/bin/env python3
"""Собирает catalog.json для приложений «Проём» из прайса компании (Excel) и правил расчёта.

Источники:
  catalog/price-list.xlsx         — прайс BENKAM (артикул, цена KZT, описание, ед. изм.)
  каталог BKT-77 (PDF, 2025)      — состав изделия: профили, уплотнители, закладные, таблица штапиков

Запуск:  python3 tools/build_catalog.py
Результат копируется в ios/OknoCalc/Resources/catalog.json и android/app/src/main/assets/catalog.json
"""
import json
import pathlib
import re

import openpyxl

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "catalog" / "price-list.xlsx"
OUT = [
    ROOT / "catalog" / "catalog.json",
    ROOT / "ios" / "OknoCalc" / "Resources" / "catalog.json",
    ROOT / "android" / "app" / "src" / "main" / "assets" / "catalog.json",
]

KIND = {"RAL + L": "profile", " L": "profile", "L": "profile", "EPDM": "gasket", "Accs": "accessory"}


def load_articles():
    ws = openpyxl.load_workbook(SRC, data_only=True).worksheets[0]
    out, seen = [], set()
    for row in ws.iter_rows(min_row=2, values_only=True):
        code, price, _cur, desc, _pack, _type, unit, kind = (list(row) + [None] * 8)[:8]
        if not code or not isinstance(desc, str) or not desc.strip():
            continue
        code = str(code).strip()
        desc = re.sub(r"\s+", " ", desc).strip()
        if "Удалить" in desc or code in seen:
            continue
        seen.add(code)
        name = desc
        if name.startswith(code):
            name = name[len(code):].lstrip(". ").strip()
        out.append({
            "code": code,
            "name": name or code,
            "price": round(float(price or 0), 2),
            "unit": "m" if str(unit or "").startswith("м") else "pc",
            "kind": KIND.get(str(kind or ""), "other"),
        })
    return out


# ---------- Цвета (RAL — порошковая покраска, анодирование EURAS, декор под дерево) ----------
RAL = [
    ("9016", "Транспортный белый", "#F1F0EA"), ("9010", "Чистый белый", "#F1ECE1"),
    ("9003", "Сигнальный белый", "#ECECE7"), ("9001", "Кремово-белый", "#E9E0D2"),
    ("1013", "Жемчужно-белый", "#E3D9C6"), ("1015", "Светлая слоновая кость", "#E6D2B5"),
    ("1014", "Слоновая кость", "#DDC49A"), ("7035", "Светло-серый", "#CBD0CC"),
    ("7047", "Телегрей 4", "#D0D0D0"), ("7040", "Серое окно", "#989EA1"),
    ("7004", "Сигнальный серый", "#969992"), ("7001", "Серебристо-серый", "#8C969D"),
    ("7037", "Пыльно-серый", "#7D7F7D"), ("7030", "Каменно-серый", "#939388"),
    ("7039", "Кварцевый серый", "#6B695F"), ("7022", "Серая умбра", "#4C4A44"),
    ("7012", "Базальтово-серый", "#5D6970"), ("7015", "Сланцево-серый", "#51565C"),
    ("7024", "Графитовый серый", "#45494E"), ("7016", "Антрацитово-серый", "#383E42"),
    ("7021", "Чёрно-серый", "#2F3234"), ("9004", "Сигнальный чёрный", "#2B2B2C"),
    ("9005", "Чёрный янтарь (глубокий чёрный)", "#0E0E10"), ("9006", "Бело-алюминиевый", "#A1A1A0"),
    ("9007", "Серый алюминий", "#878581"), ("8017", "Шоколадно-коричневый", "#45322E"),
    ("8019", "Серо-коричневый", "#403A3A"), ("8014", "Сепия коричневая", "#49392D"),
    ("8022", "Чёрно-коричневый", "#1A1718"), ("8011", "Орехово-коричневый", "#5A3A29"),
    ("8028", "Земельно-коричневый", "#4E3B2B"), ("8003", "Глиняный коричневый", "#7E4B26"),
    ("3005", "Винно-красный", "#5E2028"), ("3004", "Пурпурно-красный", "#6B1C23"),
    ("3009", "Оксидно-красный", "#642424"), ("6005", "Зелёный мох", "#0F4336"),
    ("6009", "Пихтовый зелёный", "#27352A"), ("6021", "Бледно-зелёный", "#89AC76"),
    ("5010", "Горечавково-синий", "#0E4C96"), ("5011", "Стальной синий", "#1A2B3C"),
    ("5003", "Сапфирово-синий", "#1D334A"),
]
ANOD = [
    ("C-0", "Анодирование: натуральное серебро", "#C9CCCE"),
    ("C-31", "Анодирование: светлая бронза", "#B08D57"),
    ("C-32", "Анодирование: бронза", "#8C6A3F"),
    ("C-33", "Анодирование: средняя бронза", "#6E5134"),
    ("C-34", "Анодирование: тёмная бронза", "#4B3625"),
    ("C-35", "Анодирование: чёрный", "#1E1B18"),
]
DECOR = [
    ("2178-001", "Декор: золотой дуб", "#B47A3C"),
    ("2178-007", "Декор: орех", "#6B4226"),
    ("2097-013", "Декор: махагон", "#5A2A1D"),
    ("2052-089", "Декор: мореный дуб", "#4A3326"),
]


def colors():
    out = []
    for code, name, hexv in RAL:
        out.append({"id": "ral" + code, "code": "RAL " + code, "name": name, "hex": hexv, "group": "ral",
                    "keywords": ["ral " + code, "ral" + code, code]})
    for code, name, hexv in ANOD:
        out.append({"id": "anod" + code.replace("-", "").lower(), "code": code, "name": name, "hex": hexv, "group": "anod",
                    "keywords": [code.lower(), "анод"] if code == "C-0" else [code.lower()]})
    for code, name, hexv in DECOR:
        out.append({"id": "decor" + code.replace("-", ""), "code": code, "name": name, "hex": hexv, "group": "decor",
                    "keywords": [code, name.split(": ")[1]]})
    # Синонимы по названию для чертежей без номера RAL
    words = {"ral9016": ["белый", "white"], "ral7016": ["антрацит", "anthracite"], "ral7024": ["графит", "graphite"],
             "ral9005": ["чёрный", "черный", "black"], "ral8017": ["коричнев", "brown"], "ral9006": ["серебр", "silver"],
             "decor2178001": ["дуб", "oak", "под дерево"]}
    for c in out:
        c["keywords"] += words.get(c["id"], [])
    return out


# ---------- Системы и правила расчёта ----------
# base — от чего считается количество:
#   frame_perimeter_m, impost_length_m, imposts, sash_perimeter_m, sashes, door_perimeter_m, doors,
#   glass_perimeter_m, glass_area_m2, panes, door_width_m, vert_len_m, horiz_len_m, grid_len_m, area_m2, frame_corners
def R(role, code, base, qty=1, profile=False, door_code=None, by_fill=None):
    r = {"role": role, "code": code, "base": base, "qty": qty, "profile": profile}
    if door_code:
        r["door_code"] = door_code
    if by_fill:
        r["by_fill"] = [{"min": a, "max": b, "code": c} for a, b, c in by_fill]
        r.pop("code")
    return r


# Таблица штапиков BKT 77 (каталог, лист 4-2, стандарт 2 мм; старые G0005–G0008 заменены на G0001–G0004 по прайсу)
BKT_BEADS = [(20, 28, "WDT.67.G0004"), (29, 32, "WDT.67.G0003"), (33, 36, "WDT.67.G0002"), (37, 40, "WDT.67.G0001"),
             (41, 44, "WDT.67.G0009"), (45, 48, "WDT.67.G0010"), (49, 52, "WDT.67.G0011")]
BKT_INNER_GASKET = [(20, 26, "EPDM0005"), (27, 28, "EPDM0004"), (29, 30, "EPDM0005"), (31, 32, "EPDM0004"),
                    (33, 34, "EPDM0005"), (35, 36, "EPDM0004"), (37, 38, "EPDM0005"), (39, 40, "EPDM0004"),
                    (41, 42, "EPDM0005"), (43, 44, "EPDM0004"), (45, 46, "EPDM0005"), (47, 48, "EPDM0004"),
                    (49, 50, "EPDM0005"), (51, 52, "EPDM0004")]
FST_GLASS_SUPPORT = [(10, 14, "FST.50.G0001"), (15, 20, "FST.50.G0002"), (21, 26, "FST.50.G0003"), (27, 32, "FST.50.G0004"),
                     (33, 38, "FST.50.G0005"), (39, 44, "FST.50.G0006"), (45, 50, "FST.50.G0007"), (51, 56, "FST.50.G0008"),
                     (57, 62, "FST.50.G0009")]
FST_THERMAL = [(16, 20, "PVI.CP.AF012"), (21, 26, "PVI.CP.AF018"), (27, 32, "PVI.CP.AF024"), (33, 38, "PVI.CP.AF030"),
               (39, 44, "PVI.CP.AF036"), (45, 50, "PVI.CP.AF042")]

SYSTEMS = [
    {
        "id": "bkt77", "name": "BKT 77", "description": "Тёплая оконно-дверная система (рама 69 мм, створка 77 мм), WDT.77",
        "keywords": ["bkt 77", "bkt77", "bkt-77", "бкт 77", "бкт77", "вкт 77", "вкт77", "wdt.77", "wdt 77"],
        "products": ["window", "door", "balcony_block", "partition"],
        "fill_min": 20, "fill_max": 52, "fill_default": 32,
        "deduction_fixed_mm": 70, "deduction_sash_mm": 190, "deduction_door_mm": 230,
        "rules": [
            R("Рама", "WDT.77.F0002", "frame_perimeter_m", profile=True, door_code="WDT.77.F0004"),
            R("Импост", "WDT.77.M0002", "impost_length_m", profile=True),
            R("Створка оконная", "WDT.77.V0003", "sash_perimeter_m", profile=True),
            R("Створка дверная", "WDT.77.V0007", "door_perimeter_m", profile=True),
            R("Порог", "WDT.77.P0002", "door_width_m", profile=True),
            R("Штапик", None, "glass_perimeter_m", profile=True, by_fill=BKT_BEADS),
            R("Уплотнитель стекла наружный", "EPDM0001", "glass_perimeter_m"),
            R("Уплотнитель стекла внутренний", None, "glass_perimeter_m", by_fill=BKT_INNER_GASKET),
            R("Уплотнительный жгут штапика", "EPDM0024", "glass_perimeter_m"),
            R("Центральный уплотнитель", "EPDM0041", "sash_perimeter_m"),
            R("Уплотнитель притвора", "EPDM0057", "sash_perimeter_m"),
            R("Уплотнитель пылезащитный", "EPDM0044", "sash_perimeter_m"),
            R("Уплотнитель притвора двери", "EPDM0057", "door_perimeter_m", qty=2),
            R("Уплотнитель порога", "EPDM0047", "door_width_m"),
            R("Закладная угловая рамы (наружная)", "CLX.03.W0280.L18", "frame_corners", door_code="CLX.03.W0440.L21"),
            R("Закладная угловая рамы (внутренняя)", "CLX.03.W0280.L9", "frame_corners", door_code="CLX.03.W0380.L5"),
            R("Закладная угловая створки (25,8 мм)", "CLX.03.W0280.L25.8", "sashes", qty=4),
            R("Закладная угловая створки (5 мм)", "CLX.03.W0240.L5", "sashes", qty=4),
            R("Закладная угловая двери (21 мм)", "CLX.03.W0440.L21", "doors", qty=4),
            R("Закладная угловая двери (5 мм)", "CLX.03.W0380.L5", "doors", qty=4),
            R("Выравнивающий уголок створки", "CLS.00.W12.4x13-K-Tech", "sashes", qty=4),
            R("Закладная импоста внутренняя", "Q14*27.9-K-Tech", "imposts", qty=2),
            R("Закладная импоста наружная", "K14*27.9-K-Tech", "imposts", qty=2),
            R("Соединитель импоста стягивающий", "CLS.00.W20.5-K-Tech", "imposts", qty=2),
            R("Подкладка под стеклопакет", "PVI.CP.GH005.L100", "panes", qty=4),
        ],
    },
    {
        "id": "bkf50", "name": "BKF 50", "description": "Фасадная стоечно-ригельная система 50 мм (FST.50), открывание — верхнеподвесные створки FWT.50",
        "keywords": ["bkf 50", "bkf50", "bkf-50", "бкф 50", "бкф50", "вкф 50", "fst.50", "fst 50", "фасад", "витраж"],
        "products": ["facade"],
        "fill_min": 10, "fill_max": 62, "fill_default": 32,
        "deduction_fixed_mm": 30, "deduction_sash_mm": 150, "deduction_door_mm": 150,
        "rules": [
            R("Стойка 86 мм", "FST.50.S0005", "vert_len_m", profile=True),
            R("Ригель 70 мм", "FST.50.R0004", "horiz_len_m", profile=True),
            R("Прижимная планка", "FST.50.PP001", "grid_len_m", profile=True),
            R("Декоративная крышка стойки", "FST.50.CP002", "vert_len_m", profile=True),
            R("Декоративная крышка ригеля", "FST.50.CP001", "horiz_len_m", profile=True),
            R("Термомост", None, "grid_len_m", by_fill=FST_THERMAL),
            R("Подстекольник (2 × 100 мм на стекло)", None, "panes", qty=0.2, by_fill=FST_GLASS_SUPPORT),
            R("Уплотнитель прижимной планки", "EPDM0049", "grid_len_m", qty=2),
            R("Уплотнитель внутренний", "EPDM0051", "grid_len_m", qty=2),
            R("Ригельный соединитель", "FST.50.JR001", "imposts", qty=0.1),
            R("Рама верхнеподвесной створки", "FWT.50.F0001", "sash_perimeter_m", profile=True),
            R("Створка верхнеподвесная", "FWT.50.V0001", "sash_perimeter_m", profile=True),
            R("Уплотнитель верхнеподвесной створки", "EPDM2803", "sash_perimeter_m", qty=2),
            R("Закладная угловая створки FWT.50", "CLX.02.W0140.L38", "sashes", qty=4),
            R("Закладная угловая рамы FWT.50", "CLX.02.W0140.L46", "sashes", qty=4),
        ],
    },
    {
        "id": "bko45", "name": "BKO 45", "description": "Холодная оконно-дверная система 45 мм (одинарное стекло 6 мм)",
        "keywords": ["bko 45", "bko45", "бко 45", "бко45", "холодн"],
        "products": ["window", "door", "balcony_block", "partition"],
        "fill_min": 4, "fill_max": 8, "fill_default": 6,
        "deduction_fixed_mm": 60, "deduction_sash_mm": 150, "deduction_door_mm": 180,
        "rules": [
            R("Рама", "EXHW1711", "frame_perimeter_m", profile=True, door_code="EXHW1710"),
            R("Импост", "EXHW4444", "impost_length_m", profile=True),
            R("Створка оконная", "КПС265", "sash_perimeter_m", profile=True),
            R("Створка дверная", "EXHW1709", "door_perimeter_m", profile=True),
            R("Порог", "EXHW1657", "door_width_m", profile=True),
            R("Штапик", "EXSD1656", "glass_perimeter_m", profile=True),
            R("Уплотнитель стекла наружный", "ТПУ-002ММ", "glass_perimeter_m"),
            R("Уплотнитель стекла внутренний", "ТПУ-004ММ", "glass_perimeter_m"),
            R("Уплотнитель притвора окна", "КПУ-243", "sash_perimeter_m"),
            R("Уплотнитель притвора двери", "КПУ-215", "door_perimeter_m"),
            R("Закладная угловая рамы", "EXHW1680.L41", "frame_corners", door_code="EXHW1758.L32.4"),
            R("Закладная угловая двери", "EXHW1758.L32.4", "doors", qty=4),
        ],
    },
    {
        "id": "opc40", "name": "OPC 40", "description": "Холодные двери и перегородки 40 мм (OPC.40)",
        "keywords": ["opc 40", "opc40", "опс 40", "перегородк"],
        "products": ["door", "partition"],
        "fill_min": 4, "fill_max": 32, "fill_default": 6,
        "deduction_fixed_mm": 50, "deduction_sash_mm": 120, "deduction_door_mm": 120,
        "rules": [
            R("Рама дверная", "OPC.40.F0003", "frame_perimeter_m", profile=True),
            R("Импост", "OPC.40.M0003", "impost_length_m", profile=True),
            R("Створка дверная", "OPC.40.V0001", "door_perimeter_m", profile=True),
            R("Створка", "OPC.40.V0001", "sash_perimeter_m", profile=True),
            R("Адаптер остекления", "OPC.40.A0001", "glass_perimeter_m", profile=True),
            R("Уплотнитель", "EPDM0050", "glass_perimeter_m", qty=2),
        ],
    },
    {
        "id": "wdc47", "name": "WDC 47", "description": "Холодная оконная система 47 мм (одинарное стекло 6 мм)",
        "keywords": ["wdc 47", "wdc47", "wdc.47"],
        "products": ["window", "partition"],
        "fill_min": 4, "fill_max": 8, "fill_default": 6,
        "deduction_fixed_mm": 50, "deduction_sash_mm": 130, "deduction_door_mm": 130,
        "rules": [
            R("Рама оконная", "WDC.47.F0003", "frame_perimeter_m", profile=True),
            R("Импост (рамный профиль)", "WDC.47.F0003", "impost_length_m", profile=True),
            R("Створка оконная усиленная", "WDC.47.V0004", "sash_perimeter_m", profile=True),
            R("Штапик 19 мм", "WDC.47.G0004", "glass_perimeter_m", profile=True),
            R("Уплотнитель внутреннего остекления", "EPDM0059", "glass_perimeter_m"),
            R("Уплотнитель стекла наружный", "EPDM0001", "glass_perimeter_m"),
            R("Уплотнитель притвора", "EPDM0043", "sash_perimeter_m"),
        ],
    },
]

# Цены, которых нет в прайсе (стеклопакеты, фурнитура, работа) — редактируются в приложении.
GLASS = [
    {"fill_mm": 6, "name": "Стекло 6 мм (закалённое)", "price_per_m2": 9000},
    {"fill_mm": 24, "name": "Стеклопакет 24 мм (4-16-4)", "price_per_m2": 13000},
    {"fill_mm": 28, "name": "Стеклопакет 28 мм (4-20-4)", "price_per_m2": 14500},
    {"fill_mm": 32, "name": "Стеклопакет 32 мм (4-10-4-10-4)", "price_per_m2": 17000},
    {"fill_mm": 36, "name": "Стеклопакет 36 мм (4-12-4-12-4)", "price_per_m2": 18500},
    {"fill_mm": 40, "name": "Стеклопакет 40 мм (4-14-4-14-4)", "price_per_m2": 20000},
    {"fill_mm": 44, "name": "Стеклопакет 44 мм (4-16-4-16-4)", "price_per_m2": 22000},
    {"fill_mm": 52, "name": "Стеклопакет 52 мм (6-18-4-18-6)", "price_per_m2": 26000},
]
HARDWARE = [
    {"opening": "tilt_turn", "name": "Фурнитура поворотно-откидная", "price": 28000},
    {"opening": "turn", "name": "Фурнитура поворотная", "price": 20000},
    {"opening": "tilt", "name": "Фурнитура откидная", "price": 15000},
    {"opening": "top_hung", "name": "Фурнитура верхнеподвесная", "price": 18000},
    {"opening": "slide", "name": "Раздвижной механизм", "price": 45000},
    {"opening": "door", "name": "Дверная фурнитура (петли, замок, ручки)", "price": 65000},
]


def main():
    articles = load_articles()
    codes = {a["code"]: a for a in articles}
    missing = []
    for s in SYSTEMS:
        for r in s["rules"]:
            for c in [r.get("code"), r.get("door_code")] + [x["code"] for x in r.get("by_fill", [])]:
                if c and c not in codes:
                    missing.append((s["id"], c))
    if missing:
        raise SystemExit("Нет в прайсе: %s" % missing)

    catalog = {
        "version": 2,
        "name": "BENKAM",
        "currency": "KZT",
        "vat_percent": 16,
        "markup_percent": 30,
        "waste_percent": 10,
        "labor_per_m2": 12000,
        "install_per_m2": 9000,
        "install_min": 25000,
        "two_color_percent": 15,
        "color_groups": [
            {"id": "ral", "name": "RAL (порошковая покраска)", "surcharge_percent": 0},
            {"id": "anod", "name": "Анодирование", "surcharge_percent": 10},
            {"id": "decor", "name": "Декор под дерево", "surcharge_percent": 25},
        ],
        "glass": GLASS,
        "hardware": HARDWARE,
        "colors": colors(),
        "systems": SYSTEMS,
        "articles": articles,
    }
    text = json.dumps(catalog, ensure_ascii=False, indent=1)
    for p in OUT:
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding="utf-8")
    print(f"articles={len(articles)} systems={len(SYSTEMS)} colors={len(catalog['colors'])} size={len(text)//1024}KB")


if __name__ == "__main__":
    main()
