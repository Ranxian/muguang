# -*- coding: utf-8 -*-
"""沐光内置字体子集体检：当前覆盖了哪些字、缺哪些、扩到 GB2312 后多大。"""
import sys, io
from fontTools.ttLib import TTFont

FONTS = {
    "lxgw_wen_kai": r"app/src/main/res/font/lxgw_wen_kai_regular.ttf",
    "zhi_mang_xing": r"app/src/main/res/font/zhi_mang_xing_regular.ttf",
}

SAMPLES = {
    "学校名": "吉安幼儿师范高等专科学校2期热水",
    "设备名": "洗浴热水-7栋-2层-210洗衣机烘干",
    "界面词": "取消绑定尚未领取账号管理更换手机号修改密码钱包余额去充值绑定寝室使用码后三位为手机号",
    "账单词": "洗烘设备洗衣消费充值预付费余额结算",
}

def coverage(font):
    cmap = font.getBestCmap()
    return cmap

def gb2312_chars():
    out = []
    for hi in range(0xA1, 0xF8):
        for lo in range(0xA1, 0xFF):
            try:
                ch = bytes([hi, lo]).decode("gb2312")
                out.append(ch)
            except UnicodeDecodeError:
                pass
    return out

def main():
    gb = gb2312_chars()
    gb_l1 = [c for c in gb if ord(c) > 0x2000]  # 粗略：全部汉字区
    # GB2312 一级字库 = 区 16-55（0xB0-0xD7），二级 = 区 56-87（0xD8-0xF7）
    l1 = []
    for hi in range(0xB0, 0xD8):
        for lo in range(0xA1, 0xFF):
            try:
                l1.append(bytes([hi, lo]).decode("gb2312"))
            except UnicodeDecodeError:
                pass
    l2 = []
    for hi in range(0xD8, 0xF8):
        for lo in range(0xA1, 0xFF):
            try:
                l2.append(bytes([hi, lo]).decode("gb2312"))
            except UnicodeDecodeError:
                pass
    print(f"GB2312 一级 {len(l1)} 字 / 二级 {len(l2)} 字 / 合计 {len(l1)+len(l2)}")

    for name, path in FONTS.items():
        f = TTFont(path, lazy=True)
        cmap = coverage(f)
        n_glyphs = f["maxp"].numGlyphs
        print(f"\n== {name} ==  glyphs={n_glyphs}  cmap字符数={len(cmap)}")
        for label, text in SAMPLES.items():
            missing = [c for c in text if ord(c) not in cmap and not c.isspace()]
            if missing:
                print(f"  [{label}] 缺 {len(missing)} 字: {''.join(missing[:40])}")
            else:
                print(f"  [{label}] 全覆盖")
        for label, pool in (("GB2312一级", l1), ("GB2312二级", l2)):
            have = sum(1 for c in pool if ord(c) in cmap)
            print(f"  {label}覆盖 {have}/{len(pool)} = {have/len(pool)*100:.1f}%")
        f.close()

if __name__ == "__main__":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    main()
