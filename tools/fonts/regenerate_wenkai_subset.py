# -*- coding: utf-8 -*-
"""重新生成霞鹜文楷的应用内子集（res/font/lxgw_wen_kai_regular.ttf）。

## 为什么需要这个脚本

应用把文楷用作**正文**字体（暖纸风格全量、首页 hero 副标题），而正文里大量出现
**服务端下发的动态字符串**：学校名、设备名、账单描述……2026-11 用户实测发现
「吉安幼儿师范高等专科学校2期热水」中 吉/幼 两字回退成系统字体——旧子集只按
UI 静态文案取了 1458 字，动态字符串里没进语料的字全部回退。

因此新子集的字符集**不能依赖语料**，必须覆盖整个 GB2312（6763 汉字），
再加 ASCII、中文标点与本项目用到的符号；旧子集字符也并入，保证不回退。

## 用法

    python tools/fonts/regenerate_wenkai_subset.py \
        --full <LXGWWenKai-Regular.ttf 全量字体路径> \
        --out  app/src/main/res/font/lxgw_wen_kai_regular.ttf

全量字体不入仓库（约 19MB，verify/ 已 gitignore）。本地默认会自动探测
verify/ui-directions/_fonts/LXGWWenKai-Regular.ttf；其它来源请从官方仓库
https://github.com/lxgw/LxgwWenKai/releases 下载同名 Regular TTF。

生成后跑 `python verify/font_check.py` 复核覆盖，再构建确认体积。
"""
import argparse
import io
import sys
from pathlib import Path

from fontTools import subset

ROOT = Path(__file__).resolve().parents[2]           # project/
BUILD_ROOT = ROOT.parent                              # washer_build/

# ── 字符集 ──────────────────────────────────────────────────────────────────
# 1. GB2312 全集（一级 3755 + 二级 3008 = 6763 汉字）：动态字符串的现实上界。
#    不在 GB2312 里的生僻字（喆/堃/〇等）仍会回退系统字体——可接受，出现即个案。
def gb2312_chars() -> list[str]:
    out = []
    for hi in range(0xA1, 0xF8):
        for lo in range(0xA1, 0xFF):
            try:
                out.append(bytes([hi, lo]).decode("gb2312"))
            except UnicodeDecodeError:
                pass
    return out

# 2. ASCII 可打印字符
ASCII = [chr(c) for c in range(0x20, 0x7F)]

# 3. 中文标点与本项目实际用到的符号（界面文案/账单描述/通知里出现过）
SYMBOLS = list(
    "，。、；：？！「」『』（）《》〈〉【】〔〕·—…～～￥％°＋－×÷＝"
    "‘’“”„‟†‡•◦●○◆◇★☆✓✔✗✘→←↑↓①②③④⑤⑥⑦⑧⑨⑩ⅠⅡⅢⅣⅤ"
    "㎡㎏㎜℃℉§№"
)

def build_charset() -> str:
    chars = set(gb2312_chars()) | set(ASCII) | set(SYMBOLS)
    # 旧子集字符全量并入：历史上出过的字一个都不能少
    legacy = BUILD_ROOT / "verify/ui-directions/_fonts/subset-chars.txt"
    if legacy.exists():
        chars |= set(legacy.read_text(encoding="utf-8"))
    return "".join(sorted(chars))

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--full", required=False,
                    default=str(BUILD_ROOT / "verify/ui-directions/_fonts/LXGWWenKai-Regular.ttf"))
    ap.add_argument("--out", default="app/src/main/res/font/lxgw_wen_kai_regular.ttf")
    args = ap.parse_args()

    full = Path(args.full)
    out = Path(args.out if Path(args.out).is_absolute() else ROOT / args.out)
    if not full.exists():
        sys.exit(f"找不到全量字体：{full}（见脚本头部说明）")

    text = build_charset()
    print(f"字符集：{len(text)} 字")

    opts = subset.Options()
    opts.hinting = False          # 移动端不需要 hinting，省一大截
    opts.layout_features = []     # CJK 正文用不到 GSUB/GPOS 特性
    opts.drop_tables += ["DSIG"]
    opts.notdef_outline = True
    subsetter = subset.Subsetter(options=opts)
    subsetter.populate(text=text)
    font = subset.load_font(full, opts)
    subsetter.subset(font)
    subset.save_font(font, str(out), opts)
    size_kb = out.stat().st_size / 1024
    print(f"已写出：{out}（{size_kb:.0f} KB）")


if __name__ == "__main__":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    main()
