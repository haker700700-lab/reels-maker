#!/usr/bin/env python3
"""خصائص التحرير الإضافية (v5): فلاتر احترافية، لوجو، حركة النص، شريط تقدّم، معالجة صوت، وفيديو تجريبي.
كل حاجة هنا بتبني نصوص فلاتر ffmpeg بس (من غير ما تشغّل حاجة) — فخفيفة وسريعة. engine.py هو اللي بيربطها."""
import re

# ════════════════════════════════════════════════════════════
#  الفلاتر (Looks) — كل فلتر سلسلة ffmpeg جاهزة. الشدة (0-100) بتتحكم في المزج مع الأصل.
# ════════════════════════════════════════════════════════════
LOOKS = {
    # id: (الاسم بالعربي, وصف قصير, سلسلة الفلتر)
    "none": ("بدون فلتر", "الألوان الأصلية", ""),
    "golden": ("ذهبي ✨", "دفا ذهبي زي ساعة الغروب",
               "colorbalance=rs=.10:gs=.04:bs=-.12:rm=.08:gm=.03:bm=-.10:rh=.10:gh=.06:bh=-.08,"
               "eq=contrast=1.06:saturation=1.18:brightness=0.02:gamma=1.04"),
    "sunset": ("غروب", "برتقالي وردي دافي",
               "colorbalance=rs=.14:gs=-.02:bs=-.10:rh=.12:gh=-.02:bh=.06,eq=saturation=1.25:contrast=1.05"),
    "cinematic": ("سينمائي", "ظلال تركواز وإضاءة برتقالية (Teal & Orange)",
                  "colorbalance=rs=-.10:gs=-.02:bs=.12:rh=.12:gh=.03:bh=-.10,eq=contrast=1.12:saturation=1.08:gamma=0.96"),
    "clarendon": ("كلارندون", "ضوء أعلى وألوان ساطعة (إنستجرام)",
                  "eq=contrast=1.20:saturation=1.30:brightness=0.03,colorbalance=bs=.04:bh=.05"),
    "juno": ("جونو", "دافي مع أخضر وأحمر قوي",
             "colorbalance=rh=.08:gh=.02:bh=-.06,eq=saturation=1.30:contrast=1.10:brightness=0.02"),
    "lark": ("لارك", "فاتح وهادي بلمسة تركواز",
             "eq=brightness=0.05:contrast=0.96:saturation=1.10,colorbalance=gs=.03:bs=.05:gm=.02"),
    "valencia": ("فالنسيا", "دافي باهت (فينتاج ناعم)",
                 "colorbalance=rm=.08:gm=.03:bm=-.08,eq=contrast=0.94:saturation=1.08:brightness=0.04,curves=preset=lighter"),
    "vintage": ("فينتاج", "ألوان باهتة قديمة وحبيبات",
                "curves=preset=vintage,eq=saturation=0.85:contrast=0.95,noise=alls=10:allf=t"),
    "noir": ("أبيض وأسود", "تباين قوي (Noir)",
             "hue=s=0,eq=contrast=1.35:brightness=-0.03:gamma=1.05"),
    "moon": ("مون", "أبيض وأسود ناعم بلمسة باردة",
             "hue=s=0,eq=contrast=1.12:brightness=0.03,colorbalance=bs=.04:bm=.03"),
    "vivid": ("زاهي", "ألوان مشبّعة وقوية",
              "eq=saturation=1.55:contrast=1.10,unsharp=5:5:0.5:3:3:0.0"),
    "matte": ("مات", "ظلال مرفوعة بشكل ناعم (فيلم)",
              "curves=m='0/0.07 0.5/0.52 1/0.96',eq=saturation=0.92"),
    "cool": ("بارد", "أزرق نقي ونضيف",
             "colorbalance=rs=-.08:bs=.12:rm=-.06:bm=.08:rh=-.05:bh=.08,eq=saturation=1.08"),
    "drama": ("دراما", "تباين عالي وظلال عميقة",
              "eq=contrast=1.25:saturation=0.95:gamma=0.92,curves=preset=strong_contrast"),
    "glow": ("وهج ناعم", "توهّج حالم حوالين الإضاءة",
             "eq=saturation=1.1:brightness=0.02"),   # الوهج نفسه بيتحط في glow_chain
}
LOOK_ORDER = ["none", "golden", "sunset", "cinematic", "clarendon", "juno", "lark", "valencia",
              "vintage", "noir", "moon", "vivid", "matte", "cool", "drama", "glow"]

# تحسينات إضافية (تتفعّل لوحدها)
EXTRAS = {
    "vignette": ("تعتيم الأطراف", "vignette=PI/5"),
    "grain": ("حبيبات فيلم", "noise=alls=7:allf=t"),
    "sharp": ("حدّة (Sharpen)", "unsharp=5:5:0.8:5:5:0.0"),
    "clarity": ("وضوح (Clarity)", "unsharp=13:13:0.9:13:13:0.0"),
    "stabilize": ("تثبيت خفيف", "deshake=rx=16:ry=16:edge=mirror"),
    "denoise_v": ("تنظيف الصورة", "hqdn3d=2.5:2:3:3"),
    "vibrance": ("ألوان نابضة (Vibrance)", "vibrance=intensity=0.55"),
    "lowlight": ("تفتيح الإضاءة الضعيفة", "eq=gamma=1.12:brightness=0.03"),
    "softskin": ("نعومة البشرة", "smartblur=lr=1.2:ls=0.6:lt=-2"),
}


def look_chain(look, intensity):
    """بيرجّع (chain, is_mix). chain = سلسلة فلتر. intensity 0-100: أقل من 100 بنمزج الفلتر مع الأصل."""
    look = look if look in LOOKS else "none"
    chain = LOOKS[look][2]
    return chain, max(0, min(100, int(intensity)))


def apply_look(label_in, label_out, look, intensity, tag="lk"):
    """جزء من filter_complex: بياخد [label_in] ويطلّع [label_out] بعد الفلتر والشدة. لو من غير فلتر بيرجّع ''."""
    chain, k = look_chain(look, intensity)
    if not chain or k <= 0:
        return ""
    parts = []
    base = f"[{label_in}]"
    if look == "glow":
        # وهج: نسخة مغبّشة بتتجمّع بوضع screen بنسبة بسيطة
        parts.append(f"{base}split[{tag}a][{tag}b]")
        parts.append(f"[{tag}b]gblur=sigma=18,eq=brightness=0.04[{tag}g]")
        parts.append(f"[{tag}a][{tag}g]blend=all_mode=screen:all_opacity={0.35 * k / 100:.3f},{chain}[{label_out}]")
        return ";".join(parts)
    if k >= 100:
        return f"{base}{chain}[{label_out}]"
    parts.append(f"{base}split[{tag}a][{tag}b]")
    parts.append(f"[{tag}b]{chain}[{tag}f]")
    parts.append(f"[{tag}a][{tag}f]blend=all_mode=normal:all_opacity={k / 100:.3f}[{label_out}]")
    return ";".join(parts)


def extras_chain(extras):
    """سلسلة فلاتر الإضافات المختارة (بالترتيب الصح: تنظيف ثم تثبيت ثم حدّة ثم أثر)."""
    order = ["denoise_v", "stabilize", "lowlight", "softskin", "vibrance", "clarity", "sharp", "vignette", "grain"]
    out = [EXTRAS[k][1] for k in order if k in extras and k in EXTRAS]
    return ",".join(out)


# ════════════════════════════════════════════════════════════
#  اللوجو
# ════════════════════════════════════════════════════════════
LOGO_POS = {"tl": "أعلى يسار", "tr": "أعلى يمين", "bl": "أسفل يسار", "br": "أسفل يمين", "c": "المنتصف"}


def logo_filter(label_in, logo_idx, label_out, W, H, pos="tr", size=18, opacity=90, margin=4, tag="lg"):
    """بيحط اللوجو (input رقم logo_idx) فوق [label_in]. size = نسبة عرض اللوجو من عرض الفيديو %."""
    size = max(5, min(60, int(size)))
    opacity = max(10, min(100, int(opacity)))
    lw = max(16, int(W * size / 100) // 2 * 2)
    m = max(0, int(W * max(0, min(15, int(margin))) / 100))
    # الأعلى بيتحرّك لتحت شوية عشان واجهة المنصات (الاسم/الزرار) ما تغطّيش اللوجو
    top_m = m + int(H * 0.05)
    bot_m = m + int(H * 0.12)
    xy = {
        "tl": (f"{m}", f"{top_m}"),
        "tr": (f"main_w-overlay_w-{m}", f"{top_m}"),
        "bl": (f"{m}", f"main_h-overlay_h-{bot_m}"),
        "br": (f"main_w-overlay_w-{m}", f"main_h-overlay_h-{bot_m}"),
        "c": ("(main_w-overlay_w)/2", "(main_h-overlay_h)/2"),
    }.get(pos, None) or (f"main_w-overlay_w-{m}", f"{top_m}")
    pre = f"[{logo_idx}:v]format=rgba,scale={lw}:-2"
    if opacity < 100:
        pre += f",colorchannelmixer=aa={opacity / 100:.2f}"
    return f"{pre}[{tag}];[{label_in}][{tag}]overlay=x={xy[0]}:y={xy[1]}:shortest=1:format=auto[{label_out}]"


# ════════════════════════════════════════════════════════════
#  حركة النص + شريط التقدّم + تكبير بطيء
# ════════════════════════════════════════════════════════════
TEXT_ANIMS = {"none": "ثابت", "fade": "ظهور ناعم", "slide": "ينزلق من تحت", "pop": "يظهر بنبضة"}


def text_overlay_expr(anim, H):
    """(overlay_extra_args, text_pre_filters). بيرجّع بارامترات الـ overlay + فلاتر على صورة النص قبل الدمج."""
    if anim == "fade":
        return "overlay=0:0:shortest=1", "format=rgba,fade=t=in:st=0.15:d=0.5:alpha=1"
    if anim == "slide":
        # ينزلق لفوق من 7% تحت مكانه خلال 0.5 ثانية مع ظهور
        return (f"overlay=0:y='if(lt(t,0.55),(0.55-t)/0.55*{int(H * 0.07)},0)':shortest=1",
                "format=rgba,fade=t=in:st=0.1:d=0.4:alpha=1")
    if anim == "pop":
        return "overlay=0:0:shortest=1", "format=rgba,fade=t=in:st=0.1:d=0.18:alpha=1"
    return "overlay=0:0:shortest=1", ""


def progress_bar(W, H, out_len, color="0x2DD4BF", thickness=0.006):
    """شريط تقدّم رفيع تحت زي تيك توك."""
    th = max(4, int(H * thickness)) // 2 * 2
    return (f"drawbox=x=0:y=ih-{th}:w=iw:h={th}:color=black@0.35:t=fill,"
            f"drawbox=x=0:y=ih-{th}:w='iw*min(1,t/{max(0.1, out_len):.3f})':h={th}:color={color}@0.95:t=fill")


def slow_zoom(W, H, out_len, amount=0.08):
    """تكبير بطيء متواصل (Ken Burns) من المنتصف. بيستخدم scale بـ eval=frame."""
    a = max(0.02, min(0.25, amount))
    d = max(0.5, out_len)
    return (f"scale=w='trunc({W}*(1+{a}*t/{d:.3f})/2)*2':h='trunc({H}*(1+{a}*t/{d:.3f})/2)*2':eval=frame,"
            f"crop={W}:{H}")


# ════════════════════════════════════════════════════════════
#  الصوت
# ════════════════════════════════════════════════════════════
def voice_chain(opts):
    """سلسلة فلاتر صوت الفيديو الأصلي حسب الخيارات: voice / bass / comp / denoise / gate."""
    f = []
    if opts.get("a_hp", True):
        f.append("highpass=f=70")
    if opts.get("a_denoise"):
        f.append("afftdn=nr=12:nf=-30")
    if opts.get("a_voice"):      # وضوح الكلام: قص الرنين + رفع الوضوح + ضغط خفيف
        f += ["equalizer=f=250:t=q:w=1:g=-2", "equalizer=f=3200:t=q:w=1.1:g=3",
              "acompressor=threshold=-20dB:ratio=3:attack=8:release=120:makeup=2"]
    elif opts.get("a_comp"):
        f.append("acompressor=threshold=-18dB:ratio=2.5:attack=10:release=150:makeup=1")
    if opts.get("a_bass"):
        f.append("bass=g=5:f=100:w=0.8")
    if opts.get("a_treble"):
        f.append("treble=g=3:f=6000")
    return ",".join(f)


def master_chain(opts):
    """سلسلة الماستر الأخيرة: توحيد مستوى الصوت + حماية من القطع."""
    if opts.get("a_loud", True):
        tgt = {"quiet": -18, "std": -16, "loud": -14}.get(opts.get("a_target", "std"), -16)
        return f"loudnorm=I={tgt}:TP=-1.5:LRA=11,alimiter=limit=0.95"
    return "alimiter=limit=0.95"


# ════════════════════════════════════════════════════════════
#  الفيديو التجريبي (10 ثواني) — بيتولّد على الجهاز من غير تحميل
# ════════════════════════════════════════════════════════════
def demo_source_cmd(out, seconds=10, w=1280, h=720):
    """فيديو تجريبي: خلفية متحرّكة ملوّنة + شريط حركة + صوت كلام تقريبي (نغمات متقطّعة) عشان كل الخصائص تبان.
    بيتعمل بـ lavfi فقط (من غير ملفات ولا نت)."""
    s = max(3, min(30, int(seconds)))
    vf = (f"testsrc2=size={w}x{h}:rate=30:duration={s}[v0];"
          f"gradients=size={w}x{h}:rate=30:duration={s}:speed=0.02:c0=0x1d4ed8:c1=0xf59e0b:c2=0xdc2626:c3=0x059669:nb_colors=4[g];"
          f"[v0][g]blend=all_mode=overlay:all_opacity=0.55,format=yuv420p[v]")
    # صوت: نغمة "كلام" متقطّعة + ضوضاء خفيفة (عشان تنقية الصوت يبان أثرها)
    af = (f"sine=f=220:duration={s}:sample_rate=44100,volume='0.5*gt(sin(2*PI*2.2*t),0)':eval=frame[a1];"
          f"anoisesrc=d={s}:c=pink:a=0.03:r=44100[a2];[a1][a2]amix=inputs=2:duration=first,aformat=channel_layouts=stereo[a]")
    return ["ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
            "-filter_complex", vf + ";" + af, "-map", "[v]", "-map", "[a]",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "128k", "-t", str(s), "-movflags", "+faststart", out]


# ════════════════════════════════════════════════════════════
#  الإعدادات الجاهزة (Presets)
# ════════════════════════════════════════════════════════════
PRESETS = {
    # وضع الأفضل: أحسن توليفة عامة للريلز (سريعة وخفيفة)
    "best": dict(
        feat="crop,best,face,enhance,orig,denoise,fade",
        look="cinematic", look_i=45, extras=["sharp", "vignette"],
        a_voice=True, a_loud=True, a_denoise=True, a_target="std",
        text_anim="fade", progress=True, zoom_slow=False,
    ),
    "pro": dict(
        feat="crop,best,face,enhance,orig,denoise,fade",
        look="golden", look_i=55, extras=["clarity", "vignette", "grain"],
        a_voice=True, a_bass=False, a_loud=True, a_denoise=True, a_target="loud",
        text_anim="slide", progress=True, zoom_slow=True,
    ),
}


def clean_hex(c, default="FFFFFF"):
    c = str(c or "").strip().lstrip("#")
    return c.upper() if re.fullmatch(r"[0-9a-fA-F]{6}", c) else default
