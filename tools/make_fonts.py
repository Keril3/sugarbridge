"""Builds the app's fonts in app/src/main/res/font: static weights, cut to the characters the app needs.

Run it again whenever a Chinese, Japanese or Korean text changes; FontsTest fails until then.

    python tools/make_fonts.py <noto folder> <fusion pixel folder>

<noto folder> holds the variable fonts from https://github.com/google/fonts (ofl/notosans, ofl/notoserif,
ofl/notosanssc, ofl/notosansjp, ofl/notosanskr, ofl/notoserifsc, ofl/notoserifjp, ofl/notoserifkr):
    NotoSans[wdth,wght].ttf, NotoSerif[wdth,wght].ttf,
    NotoSansSC[wght].ttf, NotoSansJP[wght].ttf, NotoSansKR[wght].ttf,
    NotoSerifSC[wght].ttf, NotoSerifJP[wght].ttf, NotoSerifKR[wght].ttf
<fusion pixel folder> holds the 12 px proportional fonts of a release of https://github.com/TakWolf/fusion-pixel-font
(fusion-pixel-font-12px-proportional-ttf-v2026.09.25.zip for this version of the app):
    fusion-pixel-12px-proportional-zh_hans.ttf, fusion-pixel-12px-proportional-ja.ttf,
    fusion-pixel-12px-proportional-ko.ttf

Noto Sans and Noto Serif keep Latin (with Turkish, German, French ...), Cyrillic (with Ukrainian), punctuation and
arrows in full. Their Chinese, Japanese and Korean parts and the pixel font for those languages keep the characters
of the app's texts in those languages and of dates and times. Any other character comes from the phone's fonts.
Needs fontTools (pip install fonttools).
"""
import io
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
OUT = RES / "font"


def strings_text(folder):
    root = ET.parse(RES / folder / "strings.xml").getroot()
    return "".join("".join(e.itertext()) for e in root.iter() if e.tag in ("string", "item"))


# Latin and Cyrillic in full, with punctuation, arrows and symbols the app shows
LATIN_CYRILLIC = (
    list(range(0x20, 0x7F)) + list(range(0xA0, 0x250)) + list(range(0x300, 0x370)) + list(range(0x400, 0x530))
    + list(range(0x1E00, 0x1F00)) + list(range(0x2000, 0x2070)) + list(range(0x20A0, 0x20C1)) + list(range(0x2100, 0x2150))
    + list(range(0x2190, 0x2200)) + [0x2212, 0x2248, 0x2260, 0x2264, 0x2265, 0x2713, 0x2715, 0x25CF, 0x25B2, 0x25BC, 0x2661]
)
COMMON = set(chr(c) for c in range(0x20, 0x7F)) | set("·•…–—«»“”‘’„。、，：；！？（）「」『』《》〈〉・ー〜％℃°×÷±→←↑↓↗↘")
DATES = {
    "zh": "年月日时分秒周星期一二三四五六七八九十上下午今天昨明前后点",
    "ja": "年月日時分秒曜火水木金土午前後今昨明一二三四五六七八九十",
    "ko": "년월일시분초요화수목금토오전후오늘어제내",
}
FOLDERS = {"zh": "values-zh-rCN", "ja": "values-ja", "ko": "values-ko"}


def cjk(chars):
    """The characters of [chars] that a CJK font is for: everything outside Latin and Cyrillic."""
    return sorted(set(ord(c) for c in chars if ord(c) >= 0x2E80) | set(ord(c) for c in COMMON))


def build(source, axes, unicodes, out):
    font = TTFont(source)
    if axes and "fvar" in font:
        font = instancer.instantiateVariableFont(font, axes)
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.notdef_outline = True
    options.glyph_names = False
    options.hinting = False
    sub = subset.Subsetter(options)
    sub.populate(unicodes=unicodes)
    sub.subset(font)
    buf = io.BytesIO()
    font.recalcTimestamp = False   # the source's date, so the same input gives the same file
    font.save(buf)
    (OUT / out).write_bytes(buf.getvalue())
    covered = set(font.getBestCmap())
    missing = [c for c in unicodes if c not in covered and c >= 0x2E80]
    print(f"{out:30} {len(buf.getvalue()) / 1024:8.1f} KB  {len(covered):5} chars  missing CJK: {len(missing)}")
    return len(buf.getvalue())


def main(noto, pixel):
    texts = {lang: strings_text(folder) + DATES[lang] for lang, folder in FOLDERS.items()}
    total = 0
    for family in ("Sans", "Serif"):
        for weight, w in (("regular", 400), ("bold", 700)):
            total += build(noto / f"Noto{family}[wdth,wght].ttf", {"wght": w, "wdth": 100}, LATIN_CYRILLIC, f"noto_{family.lower()}_{weight}.ttf")
        for lang, tag in (("zh", "SC"), ("ja", "JP"), ("ko", "KR")):
            for weight, w in (("regular", 400), ("bold", 700)):
                total += build(noto / f"Noto{family}{tag}[wght].ttf", {"wght": w}, cjk(texts[lang]), f"noto_{family.lower()}_{lang}_{weight}.ttf")
    for lang, file in (("zh", "zh_hans"), ("ja", "ja"), ("ko", "ko")):
        total += build(pixel / f"fusion-pixel-12px-proportional-{file}.ttf", None, cjk(texts[lang]), f"pixel_{lang}.ttf")
    print(f"total {total / 1024 / 1024:.2f} MB")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    sys.stdout.reconfigure(encoding="utf-8")
    main(Path(sys.argv[1]), Path(sys.argv[2]))
