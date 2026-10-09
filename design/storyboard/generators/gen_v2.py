"""Storyboard generator, version 2 (Oct 8, 2026): draws the app AS BUILT.

Run from anywhere:  python design/storyboard/generators/gen_v2.py
Reads every word the owner or a guardian sees from core/.../text/Texts.kt and Problem.kt, so the boards cannot drift
from the code (rerun after a wording change). Writes the boards and canvas.json into design/storyboard/project/.
The older gen.py / gen_main.py drew the first (planned) storyboard and are kept for history only.
"""
import html as _html
import json
import os
import re
from types import SimpleNamespace

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
OUT = os.path.join(ROOT, "design", "storyboard", "project")
TEXT_DIR = os.path.join(ROOT, "core", "src", "main", "kotlin", "com", "aegida", "core", "text")


# ------------------------------------------------------------------ words from the code
def _lit(expr):
    out = []
    for m in re.finditer(r'"((?:[^"\\]|\\.)*)"', expr):
        s = m.group(1)
        s = s.replace('\\"', '"').replace("\\n", "\n").replace("\\\\", "\\").replace("\\$", "$")
        out.append(s)
    return "".join(out)


def parse_texts():
    root = {}
    stack = [(-1, root)]
    lines = open(os.path.join(TEXT_DIR, "Texts.kt"), encoding="utf-8").read().split("\n")
    i = 0
    while i < len(lines):
        line = lines[i]
        m = re.match(r"^(\s*)object (\w+) \{", line)
        if m and m.group(2) != "Texts":
            d = {}
            stack[-1][1][m.group(2)] = d
            stack.append((len(m.group(1)), d))
        m = re.match(r"^(\s*)\}\s*$", line)
        if m and len(stack) > 1 and len(m.group(1)) == stack[-1][0]:
            stack.pop()
        m = re.match(r"^\s*const val (\w+) = (.*)$", line)
        if m:
            expr = m.group(2)
            while expr.rstrip().endswith("+"):
                i += 1
                expr += lines[i]
            stack[-1][1][m.group(1)] = _lit(expr)
        i += 1
    return root


def to_ns(d):
    return SimpleNamespace(**{k: to_ns(v) if isinstance(v, dict) else v for k, v in d.items()})


def parse_problems():
    src = open(os.path.join(TEXT_DIR, "Problem.kt"), encoding="utf-8").read()
    body = src[src.index(") {") : src.index("    ;")]
    pat = re.compile(
        r'^    (\w+)\(\s*Severity\.(\w+),\s*"((?:[^"\\]|\\.)*)",\s*(null|"(?:[^"\\]|\\.)*"),\s*whatHappened = "((?:[^"\\]|\\.)*)"(.*?)\n?    \),?$',
        re.S | re.M,
    )
    # entries are separated by "\n    )," lines or are one-liners; split on entry starts
    starts = [m.start() for m in re.finditer(r"^    [A-Z]\w+\(", body, re.M)]
    starts.append(len(body))
    out = {}
    for a, b in zip(starts, starts[1:]):
        chunk = body[a:b]
        name = re.match(r"\s*(\w+)\(", chunk).group(1)
        m = re.search(
            r'Severity\.(\w+),\s*"((?:[^"\\]|\\.)*)",\s*(null|"(?:[^"\\]|\\.)*"),\s*whatHappened = "((?:[^"\\]|\\.)*)"', chunk, re.S
        )
        fix = re.search(r"fix = Fix\.(\w+)", chunk)
        g = None if m.group(3) == "null" else _lit(m.group(3))
        out[name] = SimpleNamespace(
            name=name, severity=m.group(1), for_mom=_lit('"' + m.group(2) + '"'), guardian=g,
            what=_lit('"' + m.group(4) + '"'), fix=fix.group(1) if fix else "None",
            hides="hidesNotification = true" in chunk,
        )
    return out


X = to_ns(parse_texts())
P = parse_problems()
assert len(P) == 19, len(P)
OWNER = "Ruth"
GUARD = "Sam"


def guardian_text(problem):
    return P[problem].guardian.replace("%s", OWNER)


def gt(template):
    return template.replace("%s", OWNER)


def e(s):
    return _html.escape(s, quote=False).replace("\n", "<br>")


# ------------------------------------------------------------------ look
FONT = "font-family: 'Atkinson Hyperlegible', system-ui, sans-serif"
LIGHT = dict(bg="#FFF4CC", fg="#1C1B1A", surface="#FFFFFF", prim="#0F6B73", onprim="#FFFFFF", outline="#0F6B73",
             var="#3E4C59", good="#2E7D32", amber="#B57F00", line="#D9D2B4", dark=False)
DARK = dict(bg="#151413", fg="#FFF4CC", surface="#211F1D", prim="#7FCBD2", onprim="#151413", outline="#7FCBD2",
            var="#D9D2B4", good="#7FD18B", amber="#F4B400", line="#3A3631", dark=True)
SYS = dict(bg="#EEF1F4", fg="#1B2733", surface="#FFFFFF", prim="#3B6FB6", onprim="#FFFFFF", outline="#9AA5B1",
           var="#52606D", good="#2E7D32", amber="#B57F00", line="#D5DEE5", dark=False)
GUARD_T = dict(SYS, bg="#E6EBF0")

TILES = {"Sunflower": "#FFF4CC", "Owl": "#EADFF5", "Teapot": "#DDF1E4", "Sailboat": "#DDEFF0", "Apple": "#FDE3E1",
         "Butterfly": "#E3EBFA", "House": "#F3E8DC", "Star": "#2B3A67", "Fish": "#D9F0F5", "Tree": "#E2F2D9"}


def sun(s=40):
    return ('<svg viewBox="0 0 48 48" width="%d" height="%d" style="flex: none"><g fill="#F4B400"><circle cx="24" cy="10" r="6"></circle>'
            '<circle cx="24" cy="38" r="6"></circle><circle cx="10" cy="24" r="6"></circle><circle cx="38" cy="24" r="6"></circle>'
            '<circle cx="14" cy="14" r="6"></circle><circle cx="34" cy="34" r="6"></circle><circle cx="34" cy="14" r="6"></circle>'
            '<circle cx="14" cy="34" r="6"></circle></g><circle cx="24" cy="24" r="9" fill="#6B3E1B"></circle></svg>') % (s, s)


def shield(s=40, fill="#0F6B73"):
    return ('<svg viewBox="0 0 48 48" width="%d" height="%d" style="flex: none"><path d="M24 4 L40 10 L40 24 Q40 38 24 44 Q8 38 8 24 L8 10 Z" fill="%s"></path>'
            '<path d="M16 24 L22 30 L33 17" fill="none" stroke="#fff" stroke-width="3.6" stroke-linecap="round" stroke-linejoin="round"></path></svg>') % (s, s, fill)


def tile(s=44, picture="Sunflower"):
    return ('<div style="flex: none; width:%dpx;height:%dpx;border-radius:%dpx;background:%s;display:flex;align-items:center;justify-content:center">%s</div>'
            % (s, s, s // 4, TILES[picture], sun(int(s * 0.72))))


def banner_art(s=44):
    """The large icon on a warning banner: her picture's tile, her picture faint, the teal shield on top (PictureBanner.kt)."""
    return ('<div style="flex: none; position: relative; width:%dpx;height:%dpx;border-radius:%dpx;background:%s;overflow:hidden">'
            '<div style="position:absolute;inset:%dpx;opacity:.43">%s</div>'
            '<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center">%s</div></div>'
            % (s, s, s // 4, TILES["Sunflower"], int(s * 0.1), sun(int(s * 0.8)), shield(int(s * 0.74))))


def small_icon(T):
    return '<div style="flex:none;width:14px;height:14px">%s</div>' % shield(14, T["prim"])


# ---- text pieces (all take theme T)
def tag(T, t):
    return '<div style="font-size: 13px; font-weight: 700; letter-spacing: 0.06em; color: %s">%s</div>' % (T["var"], e(t))


def hd(T, t, s=25):
    return '<div style="font-size: %dpx; font-weight: 700; line-height: 1.18; color: %s">%s</div>' % (s, T["fg"], e(t))


def hdc(T, t, s=25):
    return '<div style="font-size: %dpx; font-weight: 700; line-height: 1.18; color: %s; text-align:center">%s</div>' % (s, T["fg"], e(t))


def bd(T, t, s=16, center=False, color=None):
    return '<div style="font-size: %dpx; line-height: 1.3; color: %s%s">%s</div>' % (s, color or T["fg"], "; text-align:center" if center else "", e(t))


def small(T, t):
    return '<div style="font-size: 13px; line-height: 1.3; color: %s">%s</div>' % (T["var"], e(t))


def btn(T, t, enabled=True):
    op = "" if enabled else "; opacity: .45"
    return ('<div style="min-height: 56px; box-sizing:border-box; border-radius: 16px; background: %s; color: %s; font-weight: 700; font-size: 19px; display:flex; '
            'align-items:center; justify-content:center; text-align:center; padding: 6px 10px; line-height:1.2%s">%s</div>' % (T["prim"], T["onprim"], op, e(t)))


def quiet(T, t, enabled=True):
    op = "" if enabled else "; opacity: .45"
    return ('<div style="min-height: 56px; box-sizing:border-box; border-radius: 16px; border: 2px solid %s; color: %s; font-weight: 700; font-size: 19px; display:flex; '
            'align-items:center; justify-content:center; text-align:center; padding: 6px 10px; line-height:1.2%s">%s</div>' % (T["outline"], T["fg"], op, e(t)))


def header(T, picture=True):
    pic = tile(40) if picture else ""
    return '<div style="display:flex; align-items:center; gap:10px">%s<div style="font-size:23px;font-weight:700;color:%s">%s</div></div>' % (pic, T["prim"], X.APP_NAME)


def mark(T, kind):
    c = T["good"] if kind == "good" else T["amber"]
    if kind == "good":
        g = '<path d="M24 52 L38 66 L68 34" fill="none" stroke="#fff" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"></path>'
    elif kind == "tap":
        g = '<line x1="48" y1="24" x2="48" y2="54" stroke="#fff" stroke-width="8" stroke-linecap="round"></line><circle cx="48" cy="70" r="5" fill="#fff"></circle>'
    else:
        g = '<line x1="30" y1="48" x2="66" y2="48" stroke="#fff" stroke-width="8" stroke-linecap="round"></line>'
    return '<div style="align-self:center"><svg viewBox="0 0 96 96" width="76" height="76"><circle cx="48" cy="48" r="48" fill="%s"></circle>%s</svg></div>' % (c, g)


def card(T, inner, bw=0):
    return ('<div style="background:%s; border-radius:16px; padding:12px; display:flex; flex-direction:column; gap:6px; border:%dpx solid %s">%s</div>'
            % (T["surface"], bw or 1, T["line"], inner))


def field(T, label, value):
    return ('<div style="border:2px solid %s; border-radius:10px; padding:4px 10px; background:%s"><div style="font-size:11px;color:%s">%s</div>'
            '<div style="font-size:16px;color:%s;min-height:20px">%s</div></div>' % (T["var"], T["surface"], T["var"], e(label), T["fg"], e(value)))


def hist_line(T, handled, title, detail, time):
    c = T["good"] if handled else T["amber"]
    return ('<div style="background:%s;border-radius:14px;padding:10px;display:flex;gap:10px;align-items:flex-start;border:1px solid %s">'
            '<div style="flex:none;width:16px;height:16px;border-radius:8px;background:%s;margin-top:4px"></div>'
            '<div style="flex:1;display:flex;flex-direction:column;gap:2px"><div style="font-size:16px;font-weight:700;line-height:1.2;color:%s">%s</div>'
            '<div style="font-size:13px;line-height:1.25;color:%s">%s</div></div><div style="font-size:12px;color:%s">%s</div></div>'
            % (T["surface"], T["line"], c, T["fg"], e(title), T["var"], e(detail), T["var"], time))


def toggle(T, on=True):
    bg = T["prim"] if on else "#9AA5B1"
    x = "26px" if on else "2px"
    return ('<div style="flex:none;width:54px;height:30px;border-radius:15px;background:%s;position:relative"><div style="position:absolute;top:3px;left:%s;'
            'width:24px;height:24px;border-radius:12px;background:#fff"></div></div>' % (bg, x))


def nav(T, active):
    items = [("Home", X.Nav.HOME), ("History", X.Nav.HISTORY), ("Settings", X.Nav.SETTINGS)]
    cells = ""
    for key, label in items:
        on = key == active
        pill = ('<div style="width:48px;height:22px;border-radius:11px;background:%s"></div>' % (T["prim"] if on else "transparent"))
        cells += ('<div style="flex:1;display:flex;flex-direction:column;align-items:center;gap:2px;font-size:12px;font-weight:700;color:%s">%s%s</div>'
                  % (T["fg"] if on else T["var"], pill, label))
    return '<div style="margin-top:auto;margin-left:-16px;margin-right:-16px;margin-bottom:-16px;padding:8px 0 10px;background:%s;border-top:1px solid %s;display:flex">%s</div>' % (T["surface"], T["line"], cells)


def banner(T, text, urgent=False, art=True, who=None):
    """A heads-up banner. Title is always 'Aegida'; text is Problem.forMom (NotificationPoster.show)."""
    lab = '<div style="font-size:11px;font-weight:700;color:%s;display:flex;align-items:center;gap:4px">%s %s &middot; now</div>' % (T["var"], small_icon(T), X.APP_NAME)
    return ('<div style="background:%s;border:1px solid %s;border-radius:18px;padding:10px;display:flex;gap:10px;align-items:flex-start;box-shadow:0 2px 8px rgba(0,0,0,.18)">'
            '%s<div style="display:flex;flex-direction:column;gap:2px">%s<div style="font-size:16px;font-weight:700;color:%s">%s</div>'
            '<div style="font-size:14px;line-height:1.25;color:%s">%s</div></div></div>'
            % (T["surface"], T["line"], banner_art(46), lab, T["fg"], X.APP_NAME, T["fg"], e(text)))


def other_notif(T, app, title, text, ico_color="#1F5FA8", dim=False):
    op = "; opacity:.5" if dim else ""
    return ('<div style="background:%s;border:1px solid %s;border-radius:18px;padding:10px;display:flex;gap:10px;align-items:flex-start%s">'
            '<div style="flex:none;width:34px;height:34px;border-radius:17px;background:%s"></div>'
            '<div style="display:flex;flex-direction:column;gap:1px"><div style="font-size:11px;font-weight:700;color:%s">%s</div>'
            '<div style="font-size:15px;font-weight:700;color:%s">%s</div><div style="font-size:14px;line-height:1.25;color:%s">%s</div></div></div>'
            % (T["surface"], T["line"], op, ico_color, T["var"], e(app), T["fg"], e(title), T["fg"], e(text)))


def fake_notif(T, title, text):
    """The scam's own pop-up, drawn in grey and brown, never red (it is a picture of the scam, not an Aegida screen)."""
    return ('<div style="background:%s;border:2px solid #6B3E1B;border-radius:18px;padding:10px;display:flex;gap:10px;align-items:flex-start">'
            '<div style="flex:none;width:34px;height:34px;border-radius:17px;background:#6B3E1B;color:#fff;font-weight:700;display:flex;align-items:center;justify-content:center">!</div>'
            '<div style="display:flex;flex-direction:column;gap:1px"><div style="font-size:11px;font-weight:700;color:%s">Chrome &middot; from a website</div>'
            '<div style="font-size:15px;font-weight:700;color:%s">%s</div><div style="font-size:14px;line-height:1.25;color:%s">%s</div></div></div>'
            % (T["surface"], T["var"], T["fg"], e(title), T["fg"], e(text)))


def ntfy(T, title, msg, urgent=False):
    """What a guardian reads in the ntfy app on her own phone: title = owner's first name, body = the guardian text."""
    pri = ('<div style="font-size:11px;font-weight:700;color:#6B3E1B;background:#F4B400;border-radius:8px;padding:1px 6px;align-self:flex-start">sent as urgent</div>' if urgent else "")
    return ('<div style="background:%s;border:1px solid %s;border-radius:18px;padding:10px;display:flex;gap:10px;align-items:flex-start">'
            '<div style="flex:none;width:34px;height:34px;border-radius:9px;background:#317F6F"></div>'
            '<div style="display:flex;flex-direction:column;gap:2px"><div style="font-size:11px;font-weight:700;color:%s">ntfy &middot; her private topic</div>'
            '<div style="font-size:16px;font-weight:700;color:%s">%s</div><div style="font-size:14px;line-height:1.3;color:%s">%s</div>%s</div></div>'
            % (T["surface"], T["line"], T["var"], T["fg"], e(title), T["fg"], e(msg), pri))


def sysrow(T, label, sub="", right=""):
    s = '<div style="font-size:12px;color:%s">%s</div>' % (T["var"], e(sub)) if sub else ""
    return ('<div style="display:flex;align-items:center;justify-content:space-between;gap:8px;padding:9px 0;border-bottom:1px solid %s">'
            '<div><div style="font-size:15px;font-weight:600;color:%s">%s</div>%s</div>%s</div>' % (T["line"], T["fg"], e(label), s, right))


# ------------------------------------------------------------------ phone + boards
SHELL = "#1B2733"


def phone(n, title, body, cap, T=LIGHT, header_on=True, picture=True, status=None):
    head = header(T, picture) if header_on else ""
    num = ('<div style="width:34px;height:34px;border-radius:17px;background:#0F6B73;color:#fff;font-weight:700;font-size:19px;display:flex;align-items:center;justify-content:center">%s</div>' % n) if n != "" else '<div style="width:34px"></div>'
    st = ""
    if status:
        st = '<span style="font-size:11px;font-weight:700;letter-spacing:.06em;color:#8A4B00;background:#FFF1D6;border:1px solid #B45309;border-radius:8px;padding:2px 6px;margin-left:8px">%s</span>' % status
    return ('<div style="width:300px;flex:none;display:flex;flex-direction:column;gap:14px">'
            '<div style="display:flex;align-items:center;gap:10px;min-height:46px">%s<div style="font-weight:700;font-size:20px;line-height:1.1">%s%s</div></div>'
            '<div style="width:300px;height:600px;box-sizing:border-box;border:7px solid %s;border-radius:34px;background:%s;overflow:hidden;display:flex;flex-direction:column;padding:16px;gap:12px;color:%s">%s%s</div>'
            '<div style="font-size:16px;line-height:1.35;color:#3E4C59">%s</div></div>'
            % (num, e(title), st, SHELL, T["bg"], T["fg"], head, body, e(cap)))


ARROW = ('<div style="width:56px;flex:none;margin-top:338px;display:flex;justify-content:center"><svg width="32" height="20" viewBox="0 0 32 20">'
         '<path d="M2 10H28M20 3l8 7-8 7" fill="none" stroke="#7B8794" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"></path></svg></div>')
GAP = '<div style="width:28px;flex:none"></div>'


def panel(title, lines, w=300, planned=False, h=None, note=None):
    """A card that is not a phone screen: a 'what it does not do' list, a rule, or a planned feature."""
    border = "2px dashed #7B8794" if planned else "1px solid #D5DEE5"
    bg = "#F3F5F7" if planned else "#FFFFFF"
    tg = ""
    if planned:
        tg = '<div style="align-self:flex-start;font-size:13px;font-weight:700;letter-spacing:.08em;color:#52606D;border:2px solid #7B8794;border-radius:8px;padding:2px 8px">PLANNED, NOT BUILT</div>'
    hh = "min-height:%dpx;" % h if h else ""
    li = "".join('<div style="font-size:16px;line-height:1.35;color:#1B2733">%s</div>' % ln for ln in lines)
    nt = '<div style="font-size:14px;line-height:1.35;color:#52606D">%s</div>' % note if note else ""
    return ('<div style="width:%dpx;flex:none;box-sizing:border-box;%sbackground:%s;border:%s;border-radius:20px;padding:20px;display:flex;flex-direction:column;gap:10px">'
            '%s<div style="font-size:21px;font-weight:700;line-height:1.2">%s</div>%s%s</div>' % (w, hh, bg, border, tg, e(title), li, nt))


def row(items, arrows=True, label=None):
    lab = ('<div style="font-size:14px;font-weight:700;letter-spacing:.08em;color:#0F6B73">%s</div>' % e(label)) if label else ""
    sep = ARROW if arrows else GAP
    return '<div style="display:flex;flex-direction:column;gap:14px">%s<div style="display:flex;align-items:flex-start">%s</div></div>' % (lab, sep.join(items))


ROW_H = 800  # one row of phones, with its caption
BOARDS = []  # (file, canvas title, height, width)


def board(fname, title, kicker, head, sub, rows, built, emu, W=2200, extra_note=None):
    n = len(rows)
    H = 112 + 190 + n * (ROW_H + 30) + 80
    if built:
        pill = '<span style="font-size:13px;font-weight:700;letter-spacing:.08em;color:#fff;background:#1E7A46;border-radius:8px;padding:3px 10px">BUILT</span>'
        verifier = "Emulator check: %s" % emu
    else:
        pill = '<span style="font-size:13px;font-weight:700;letter-spacing:.08em;color:#fff;background:#52606D;border-radius:8px;padding:3px 10px">PLANNED</span>'
        verifier = "Nothing here is built yet. %s" % emu
    foot = ('<div style="margin-top:auto;display:flex;align-items:center;gap:14px;border-top:2px solid #C9D3DB;padding-top:14px;font-size:17px;color:#3E4C59">%s<div>%s</div></div>'
            % (pill, e(verifier)))
    nt = '<div style="font-size:16px;line-height:1.35;color:#52606D;max-width:1500px">%s</div>' % e(extra_note) if extra_note else ""
    page = f'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>{_html.escape(title)}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link href="https://fonts.googleapis.com/css2?family=Atkinson+Hyperlegible:wght@400;700&display=swap" rel="stylesheet">
<style>
body{{margin:0}}
</style>
</helmet>
<div style="width: {W}px; height: {H}px; box-sizing: border-box; padding: 56px; background: #EAF0F3; {FONT}; color: #1B2733; display: flex; flex-direction: column; gap: 30px">
<div style="display: flex; flex-direction: column; gap: 8px">
<div style="font-size: 15px; font-weight: 700; letter-spacing: 0.08em; color: #0F6B73">{e(kicker)}</div>
<h1 style="margin: 0; font-size: 44px; line-height: 1.05">{e(head)}</h1>
<div style="font-size: 20px; line-height: 1.35; color: #3E4C59; max-width: 1500px">{e(sub)}</div>
{nt}
</div>
{"".join(rows)}
{foot}
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{W},"height":{H}}}}}'>
class Component extends DCLogic {{
renderVals() {{
return {{}};
}}
}}
</script>
</body>
</html>
'''
    with open(os.path.join(OUT, fname), "w", encoding="utf-8", newline="\n") as f:
        f.write(page)
    BOARDS.append((fname, title, H, W))


def android(T, title, rows_html, note=None):
    """A page of Android's own Settings (grey, plain), which Aegida opens but does not draw."""
    nt = small(T, note) if note else ""
    return ('<div style="font-size:12px;font-weight:700;letter-spacing:.06em;color:#52606D">ANDROID SETTINGS (NOT AEGIDA)</div>'
            '<div style="font-size:22px;font-weight:700;color:%s">%s</div>%s%s' % (T["fg"], e(title), card(T, rows_html), nt))


def S(T=LIGHT):
    return T


# ============================================================ BOARD 0: first run
def board_setup():
    T = LIGHT
    N = 6
    step = lambda k: tag(T, X.Setup.STEP.replace("%s", str(k), 1).replace("%s", str(N), 1))
    welcome = (header(T, False) and "") + '<div style="display:flex;flex-direction:column;align-items:stretch;gap:16px;margin-top:20px"><div style="align-self:center">%s</div>%s%s%s</div>' % (
        shield(120), hdc(T, X.Setup.WELCOME_TITLE, 28), bd(T, X.Setup.WELCOME_BODY, 16, True), btn(T, X.Setup.WELCOME_START))
    p0 = phone(0, "Welcome", welcome, "Shown once, before any question. No safety picture yet, because she has not chosen one. Skipped if she has already started.", header_on=True, picture=False)
    p1 = phone(1, "About you and your guardian",
               step(1) + hd(T, X.Setup.ABOUT_TITLE, 23) + field(T, X.Setup.YOUR_NAME, OWNER) + bd(T, X.Setup.GUARDIAN_BODY, 14)
               + field(T, X.Setup.GUARDIAN_NAME, GUARD) + field(T, X.Setup.GUARDIAN_EMAIL, "sam@example.org") + field(T, X.Setup.GUARDIAN_PHONE, "")
               + btn(T, X.Common.CONTINUE),
               "One guardian only. Name, their email and (optional) their phone number for the Call button. Continue stays dimmed until name, guardian and a valid email are filled in. The screen scrolls.")
    tiles = "".join(
        '<div style="display:flex;flex-direction:column;align-items:center;gap:2px"><div style="border-radius:16px;%s">%s</div><div style="font-size:13px">%s</div></div>'
        % ("outline:4px solid %s;outline-offset:1px" % T["outline"] if nm == "Sunflower" else "", tile(70, nm), nm) for nm in TILES)
    p2 = phone(2, "Safety picture", step(2) + hd(T, X.Setup.PICTURE_TITLE, 23) + bd(T, X.Setup.PICTURE_BODY, 14)
               + '<div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;overflow:hidden">%s</div>' % tiles,
               "Ten pictures (every tile here shows the sunflower drawing for layout only; the app has ten different drawings), two to a row; she scrolls to Continue. Her pick appears in the header from now on. The same grid is used under My settings (Change).")
    p3 = phone(3, "Tell your guardian = the invite email",
               step(3) + hd(T, X.Setup.INVITE_TITLE, 23) + bd(T, X.Setup.INVITE_BODY.replace("%s", GUARD), 15)
               + btn(T, X.Setup.INVITE_WRITE) + bd(T, X.Setup.INVITE_NO_EMAIL_APP, 13, color=T["var"]) + quiet(T, X.Setup.INVITE_SENT, False),
               "The button opens her own email app with the email drafted (next screen of this board). 'I sent it' is dimmed until she has pressed the first button. The grey line shows only if the phone has no email app.")
    p4 = phone(4, "Alerts yes / no", step(4) + hd(T, X.Setup.ALERTS_TITLE, 21) + bd(T, X.Setup.ALERTS_BODY.replace("Ruth", OWNER), 15)
               + btn(T, X.Setup.ALERTS_YES) + quiet(T, X.Setup.ALERTS_NO),
               "Her choice, once. 'No' means Aegida never messages anyone; she can switch it on later under My settings.")
    r1 = row([p0, p1, p2, p3, p4])
    p5 = phone(5, "Notification access and banners", step(5) + hd(T, X.Setup.ACCESS_TITLE, 22) + bd(T, X.Setup.ACCESS_BODY, 15)
               + bd(T, X.Setup.ACCESS_STEP, 15) + btn(T, X.Setup.ACCESS_OPEN) + btn(T, X.Setup.ACCESS_ALLOW_BANNERS) + quiet(T, X.Common.NOT_NOW),
               "Shown with nothing granted yet. Each part is replaced by its done line (\"%s\" / \"%s\") once she has done it, and Continue replaces Not now when both are on." % (X.Setup.ACCESS_DONE, X.Setup.BANNERS_DONE))
    steps = X.Setup.FILTER_STEPS.replace("%s", X.Setup.FILTER_STEPS and "security.cloudflare-dns.com")
    p6 = phone(6, "Block scam websites", step(6) + hd(T, X.Setup.FILTER_TITLE, 22) + bd(T, X.Setup.FILTER_BODY, 14)
               + '<div style="font-size:12.5px;line-height:1.3">%s</div>' % e(steps) + bd(T, X.Setup.FILTER_NOT_YET, 14)
               + btn(T, X.Setup.FILTER_COPY_AND_OPEN) + quiet(T, X.Setup.FILTER_CHECK),
               "One big button: it copies the address and opens Android's network settings. Aegida cannot switch it on for her; it reads the result when she comes back. Not on yet shows 'Not now'; on shows 'Website protection is on.' and Continue.")
    pb = phone(7, "Auto Blocker (Samsung only)", tag(T, X.Setup.STEP.replace("%s", "7", 1).replace("%s", "7", 1)) + hd(T, X.Setup.BLOCKER_TITLE, 23) + bd(T, X.Setup.BLOCKER_BODY, 15)
               + btn(T, X.Setup.BLOCKER_SHOW) + quiet(T, X.Setup.BLOCKER_IS_ON) + quiet(T, X.Common.NOT_NOW),
               "Only on a phone where Samsung's feature may exist, so the whole flow is 7 steps there and 6 elsewhere. Aegida cannot read this switch: 'I turned it on' is recorded as her word, never shown as checked.")
    pf = phone(8, "All set", hd(T, X.Setup.DONE_TITLE, 28) + bd(T, X.Setup.DONE_BODY, 17) + btn(T, X.Common.DONE),
               "No step number. Done leaves setup for good; the first look at the phone is then saved as the baseline for 'Was that you?' questions.")
    # the email
    body = X.Invite.BODY.replace("%s", GUARD, 1).replace("%s", "aegida_xxxxxxxx", 2).replace("%s", OWNER, 1)
    em = (tag(GUARD_T, "HER EMAIL APP, WRITTEN FOR HER") + card(GUARD_T, '<div style="font-size:12px"><b>To</b> sam@example.org</div><div style="font-size:14px;font-weight:700">%s</div>'
          '<div style="font-size:11.5px;line-height:1.3;white-space:normal">%s</div>' % (e(X.Invite.SUBJECT.replace("%s", OWNER)), e(body)))
          + small(GUARD_T, "She only presses Send. The topic name here is a placeholder for the random private name Aegida makes."))
    pe = phone("", "The invite email", em, "Not an Aegida screen: it opens in her own email app (a chooser if she has several). Sam installs ntfy from the link and opens her private link to receive messages.", T=GUARD_T, header_on=False)
    r2 = row([p5, p6, pb, pf, pe])
    board("Setup.dc.html", "Setup", "SCENARIO 1 · FIRST RUN", "First run: a welcome, then six steps (seven on Samsung)",
          "She sees the welcome, then About you and your guardian, Safety picture, Tell your guardian, Alerts, Notification access, Block scam websites, [Samsung only: Auto Blocker], All set. Every word below is read from Texts.kt.",
          [r1, r2], True, "no emulator check yet walks the real first-run screens (every emulator scenario seeds a finished setup instead; `shots` photographs Home only). JVM/Robolectric UI tests cover the screens (build plan 1h).")


# ============================================================ BOARD 1: Home
def board_home():
    L, D = LIGHT, DARK
    ok = lambda T: (mark(T, "good") + hdc(T, X.Home.ALL_GOOD, 27) + bd(T, X.Home.CHECKED_TODAY.replace("%s", "9:12 am"), 17, True) + bd(T, X.Home.NO_KNOWN_PROBLEMS, 17, True)
                    + quiet(T, X.Home.CALL_A_GUARDIAN) + nav(T, "Home"))
    p1 = phone(1, "Nothing is wrong", ok(L), "'Checked today at ...', then 'No known problems found.' It makes no promise beyond what it found. Her picture and the name Aegida stay at the top of every real screen.")
    p2 = phone(2, "The same, dark theme", ok(D), "Dark theme follows the phone's setting. Same words, green and amber are lighter, never red.", T=D)
    p3 = phone(3, "One thing needs a tap", mark(L, "tap") + hdc(L, X.Home.NEEDS_ONE, 27) + bd(L, P["SiteNotifications"].for_mom, 17, True)
               + btn(L, X.Home.FIX_MY_PHONE) + quiet(L, X.Home.CALL_A_GUARDIAN) + nav(L, "Home"),
               "The first open problem's sentence is Problem.forMom. With several it says '%s'-style counts (\"%s\"); the button handles the first one." % ("", X.Home.NEEDS_MANY.replace("%s", "2")))
    p4 = phone(4, "Could not check today", mark(L, "unknown") + hdc(L, X.Home.COULDNT_CHECK, 27) + bd(L, X.Home.COULDNT_CHECK_WHY, 17, True)
               + bd(L, X.Home.LAST_CHECKED.replace("%s", "yesterday at 9:12 am"), 17, True) + quiet(L, X.Home.CALL_A_GUARDIAN) + nav(L, "Home"),
               "The check did not finish. A dash, not a warning. The 'Last checked' line is the time the last good check finished.")
    p5 = phone(5, "Never checked yet", mark(L, "unknown") + hdc(L, X.Home.NEVER_CHECKED, 27) + bd(L, X.Home.NEVER_CHECKED_WHY, 17, True)
               + quiet(L, X.Home.CALL_A_GUARDIAN) + nav(L, "Home"),
               "Only on the very first minutes after setup, before the first check has finished.")
    board("App.dc.html", "Home", "SCENARIO 2 · HOME", "Home: nothing wrong, or could not check",
          "One mark, one sentence, one big button. HomeState decides which of three screens she gets; the screen only draws it. Other dates read 'Checked yesterday at ...' or 'Checked on ...'.",
          [row([p1, p2, p3, p4, p5], arrows=False)], True,
          "`scripts/emulator-checks.sh shots` photographs Home in light and dark at 100% and 200% font. No emulator check yet for the 'I couldn't check today' screen.")


# ============================================================ BOARD 2: fake virus pop-ups
def board_popup():
    L = LIGHT
    sd = SYS
    pr = P["SiteNotifications"]
    p1 = phone(1, "The fake alarm arrives", tag(sd, "HER PHONE · NOTIFICATIONS") + fake_notif(sd, "YOUR PHONE IS INFECTED!", "3 viruses found. Tap now to clean your phone.")
               + other_notif(sd, "Messages", "Linda", "See you Sunday for lunch!") + fake_notif(sd, "YOUR PHONE IS INFECTED!", "Virus alert. Tap to remove.")
               + bd(sd, "It comes back about every minute.", 15, color="#6B3E1B"),
               "It is not a virus. A website she once allowed to send notifications is posting fake alarms. Drawn here in brown, as a picture of the scam.", T=sd, header_on=False)
    p2 = phone(2, "Aegida hides it and warns her", tag(sd, "HER PHONE · NOTIFICATIONS") + banner(sd, pr.for_mom)
               + other_notif(sd, "Messages", "Linda", "See you Sunday for lunch!") + small(sd, "Chrome's fake alarms are cancelled the moment they appear. Linda's message is left alone. The banner carries her picture over the Aegida shield; a banner without it did not come from her Aegida."),
               "Only website notifications are hidden. The banner title is always 'Aegida'; the text is Problem.forMom.", T=sd, header_on=False)
    p3 = phone(3, "Home", mark(L, "tap") + hdc(L, X.Home.NEEDS_ONE, 27) + bd(L, pr.for_mom, 16, True) + btn(L, X.Home.FIX_MY_PHONE) + quiet(L, X.Home.CALL_A_GUARDIAN) + nav(L, "Home"),
               "Tapping the banner or 'Fix my phone' does the same thing.")
    p4 = phone(4, "The website's own switch", android(sd, "Notifications from this site", sysrow(sd, "Show notifications", "the website that sent the warnings", toggle(sd, True)) + sysrow(sd, "Sound", "", toggle(sd, True)),
                                                     "Android's own page for that one website. She turns the switch off. Aegida counts it as seen once she has been there, because these warnings never come back by themselves."),
               "Fix is 'Settings, then acknowledge': it opens that website's notification switch, not a hunt through menus.", T=sd, header_on=False)
    p5 = phone(5, "The guardian is told", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, guardian_text("SiteNotifications"))
               + small(GUARD_T, "Coarse on purpose: no website name, no message text, no links. Sent only if she said yes to alerts."),
               "The title is her first name; the body is the guardian template with her name filled in.", T=GUARD_T, header_on=False)
    p6 = phone(6, "History", phone_history_site(L, pr),
               "Green: handled, because hiding the alarms is the fix.")
    board("PopupGuard.dc.html", "Fake virus pop-ups from a website", "SCENARIO 3 · FAKE VIRUS POP-UPS", "A website sends fake virus alarms",
          "Chrome website-notification spam. Aegida hides the notification, warns her, opens that website's own notification switch, and tells her guardian.",
          [row([p1, p2, p3, p4, p5, p6])], True,
          "`emulator-checks.sh scenarios` (Chrome's alarm hidden, Aegida's banner shown) and `popup-page` (the fixture page).")


# History helpers need Outcome words; kept here so the board code stays short.
OUTCOME = {
    "OwnerWarned": "You were warned.", "GuardianTold": "Your guardian was told.", "GuardianNotReached": "Aegida could not reach your guardian.",
    "GuardianNeverReached": "Aegida could not reach your guardian, and gave up.", "GuardianReminded": "Your guardian was reminded.",
    "Fixed": "Fixed.", "Acknowledged": "You saw it.",
}


def _read_outcomes():
    src = open(os.path.join(ROOT, "core", "src", "main", "kotlin", "com", "aegida", "core", "alert", "AlertPorts.kt"), encoding="utf-8").read()
    blk = src[src.index("enum class Outcome"):]
    blk = blk[: blk.index("}")]
    found = dict(re.findall(r'(\w+)\("((?:[^"\\]|\\.)*)"\)', blk))
    assert found == OUTCOME, found  # the table above is checked against the code; update both if wording changes


_read_outcomes()


def phone_history_site(T, pr):
    return (hd(T, X.History.TITLE, 25) + tag(T, X.History.TODAY) + hist_line(T, True, pr.what, OUTCOME["OwnerWarned"] + " " + OUTCOME["GuardianTold"], "9:41 am")
            + small(T, X.History.KEPT) + nav(T, "History"))


# ============================================================ BOARD 3: message with a link
def board_links():
    L, sd = LIGHT, SYS
    sc, lk = P["ScamLink"], P["LookalikeLink"]
    msg = lambda who, text: other_notif(sd, "Messages", who, text, "#1F5FA8")
    def advice(pr):
        return hd(L, pr.for_mom, 25) + bd(L, X.Home.SCAM_ADVICE, 18, True) + btn(L, X.Home.GOT_IT)
    a1 = phone(1, "A listed scam link arrives", tag(sd, "HER PHONE") + msg("Bank alert", "Your account is locked. Verify at login-secure-check.example/login"),
               "The link is on the daily scam list. Aegida reads the text of the notification and never opens the link.", T=sd, header_on=False)
    a2 = phone(2, "Warned, message left in place", tag(sd, "HER PHONE") + banner(sd, sc.for_mom) + msg("Bank alert", "Your account is locked. Verify at login-secure-check.example/login"),
               "The message stays where it is (it could be from family). Only the warning is added.", T=sd, header_on=False)
    a3 = phone(3, "The 'Got it' page", advice(sc), "Home's button opens this page. 'Got it' marks the warning seen; nothing is sent to her guardian for it.")
    a4 = phone(4, "The guardian is told", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, guardian_text("ScamLink")), "No link and no message text in the guardian's copy.", T=GUARD_T, header_on=False)
    a5 = panel("What it does not do", [
        "It does not flag links on shared hosts (for example a Google Sites page or a link shortener), because one address can be many people.",
        "It does not flag ordinary links: a link that is not on the scam list and does not imitate a company is left alone, with no banner and no message to a guardian.",
        "It never opens, fetches or follows a link from a message.",
        "A link it does not flag has only not been found on any list; Aegida never vouches for it."], w=300,
        note="Checked on the emulator: shared-host and plain links raise nothing.")
    r1 = row([a1, a2, a3, a4, a5], label="A. THE LINK IS ON THE SCAM LIST")
    b1 = phone(1, "A look-alike link arrives", tag(sd, "HER PHONE") + msg("Parcel Service", "Your parcel is held. Pay the fee: paypa1-help.example/pay"),
               "A link written to look like a company she knows.", T=sd, header_on=False)
    b2 = phone(2, "Warned, message left in place", tag(sd, "HER PHONE") + banner(sd, lk.for_mom) + msg("Parcel Service", "Your parcel is held. Pay the fee: paypa1-help.example/pay"),
               "Same behaviour as a listed link, with its own wording.", T=sd, header_on=False)
    b3 = phone(3, "The 'Got it' page", advice(lk), "Same page and same button; the headline is the look-alike sentence.")
    b4 = phone(4, "The guardian is told", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, guardian_text("LookalikeLink")), "The company is not named.", T=GUARD_T, header_on=False)
    b5 = phone(5, "History", hd(L, X.History.TITLE, 25) + tag(L, X.History.TODAY)
               + hist_line(L, False, lk.what, OUTCOME["OwnerWarned"] + " " + OUTCOME["GuardianTold"], "9:41 am")
               + hist_line(L, True, sc.what, OUTCOME["OwnerWarned"] + " " + OUTCOME["GuardianTold"] + " " + OUTCOME["Acknowledged"], "9:20 am") + small(L, X.History.KEPT) + nav(L, "History"),
               "Amber until she taps 'Got it'; then it reads 'You saw it.' and turns green.")
    r2 = row([b1, b2, b3, b4, b5], label="B. THE LINK PRETENDS TO BE A COMPANY")
    board("ScamText.dc.html", "A message with a link", "SCENARIO 4 · MESSAGE WITH A LINK", "A message arrives with a link",
          "Aegida reads message notifications, never fetches the link, and warns her about a listed scam link or a look-alike company link. Shared hosts and ordinary links are not flagged.",
          [r1, r2], True, "`emulator-checks.sh scenarios` (listed link, look-alike link, message left in place, shared hosts not flagged); `emulator_live.py` steps 5 and 6.")


# ============================================================ BOARD 4: website protection
def board_filter():
    L, sd = LIGHT, SYS
    pr = P["WebsiteFilterOff"]
    steps = X.Setup.FILTER_STEPS.replace("%s", "security.cloudflare-dns.com")
    on = lambda: hd(L, X.Setup.FILTER_TITLE, 24) + bd(L, X.Setup.FILTER_BODY, 14) + '<div style="font-size:12.5px;line-height:1.3">%s</div>' % e(steps)
    p1 = phone(1, "Website protection is off", mark(L, "tap") + hdc(L, X.Home.NEEDS_ONE, 27) + bd(L, pr.for_mom, 17, True) + btn(L, X.Home.FIX_MY_PHONE) + quiet(L, X.Home.CALL_A_GUARDIAN) + nav(L, "Home"),
               "Aegida read the phone's Private DNS setting and it is not the protective one.")
    p2 = phone(2, "The banner", tag(sd, "HER PHONE") + banner(sd, pr.for_mom), "The same sentence as a heads-up banner.", T=sd, header_on=False)
    p3 = phone(3, "Aegida's guided page", on() + bd(L, X.Setup.FILTER_NOT_YET, 14) + btn(L, X.Setup.FILTER_COPY_AND_OPEN) + quiet(L, X.Setup.FILTER_CHECK) + quiet(L, X.Common.NOT_NOW),
               "'Fix my phone' opens this page. It is the same page as in first run (and from My settings > Block scam websites).")
    p4 = phone(4, "Android's Private DNS", android(sd, "Private DNS", sysrow(sd, "Off") + sysrow(sd, "Automatic") + sysrow(sd, "Private DNS provider hostname", "security.cloudflare-dns.com (she pastes it)"), "Aegida cannot change this for her. The address is already copied."),
               "The big button copied the address and opened Android's network settings; she pastes and saves.", T=sd, header_on=False)
    p5 = phone(5, "Back in Aegida: it is on", on() + bd(L, X.Setup.FILTER_ON, 15) + btn(L, X.Common.DONE),
               "Aegida checks when she returns (and 'Check it' checks again). Only when it has read the setting does it say 'on'.")
    p6 = phone(6, "The guardian is told", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, guardian_text("WebsiteFilterOff")), "Sent when it was turned off; she gets one calm sentence, not a hunt.", T=GUARD_T, header_on=False)
    p7 = phone(7, "My settings", hd(L, X.Settings.TITLE, 25) + card(L, '<div style="font-size:17px;font-weight:700">%s</div><div style="font-size:16px;color:%s">%s</div>%s' % (e(X.Settings.WEBSITE_PROTECTION), L["var"], X.Settings.OFF, quiet(L, X.Setup.FILTER_TITLE)))
               + small(L, "Shows 'On' when read as on. If it cannot be read: '%s'." % X.Settings.CANT_TELL), "My settings shows the same state, with a way back to the guided page while it is off.")
    board("Filter.dc.html", "Website protection turned off", "SCENARIO 5 · WEBSITE PROTECTION", "Website protection (Private DNS) is off",
          "Aegida reads the phone's Private DNS setting. If it is off she gets one sentence and a guided fix; Aegida never switches it for her.",
          [row([p1, p2, p3, p4, p5, p6, p7])], True,
          "No emulator scenario raises this warning yet; `emulator-checks.sh spike` confirms the Private DNS hostname can be set and read on the emulator.", W=2640)


# ============================================================ BOARD 5: lost notification access
def board_access():
    L, sd = LIGHT, SYS
    a, b = P["ListenerOff"], P["NotificationsBlocked"]
    p1 = phone(1, "Lost notification access", mark(L, "tap") + hdc(L, X.Home.NEEDS_ONE, 27) + bd(L, a.for_mom, 17, True) + btn(L, X.Home.FIX_MY_PHONE) + quiet(L, X.Home.CALL_A_GUARDIAN) + nav(L, "Home"),
               "Without notification access Aegida cannot see fake warnings or message links, so this is a problem on its own.")
    p2 = phone(2, "Android's access screen", android(sd, "Notification access", sysrow(sd, "Aegida", "", toggle(sd, False)), "'Fix my phone' opens this list. She turns Aegida back on. The next check sees it and the question clears."),
               "Fix: open the Android screen; nothing to confirm in Aegida.", T=sd, header_on=False)
    p3 = phone(3, "Cannot show warnings", mark(L, "tap") + hdc(L, X.Home.NEEDS_ONE, 27) + bd(L, b.for_mom, 17, True) + btn(L, X.Home.FIX_MY_PHONE) + quiet(L, X.Home.CALL_A_GUARDIAN) + nav(L, "Home"),
               "Aegida's own banners are switched off or blocked, so it can only tell her on Home. This is why Home always shows the open problem.")
    p4 = phone(4, "Aegida's notification settings", android(sd, "Aegida notifications", sysrow(sd, "All Aegida notifications", "", toggle(sd, False)) + sysrow(sd, "Needs a tap", "", toggle(sd, False)), "The fix opens Aegida's own notification page."),
               "Same one-tap fix: the right Android page.", T=sd, header_on=False)
    p5 = phone(5, "The guardian is told", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, guardian_text("ListenerOff")) + ntfy(GUARD_T, OWNER, guardian_text("NotificationsBlocked")),
               "One message for each kind (shown together here).", T=GUARD_T, header_on=False)
    p6 = phone(6, "History", hd(L, X.History.TITLE, 25) + tag(L, X.History.TODAY)
               + hist_line(L, False, b.what, OUTCOME["OwnerWarned"] + " " + OUTCOME["GuardianTold"], "11:05 am")
               + hist_line(L, True, a.what, OUTCOME["OwnerWarned"] + " " + OUTCOME["GuardianTold"] + " " + OUTCOME["Fixed"], "8:30 am") + small(L, X.History.KEPT) + nav(L, "History"),
               "Fixed is written when the next check finds it working again.")
    board("Access.dc.html", "Aegida lost notification access", "SCENARIO 6 · AEGIDA CANNOT WATCH OR WARN", "Aegida lost notification access, or cannot show warnings",
          "Two plain problems with the same fix shape: tap, land on the right Android page, switch it back on.",
          [row([p1, p2, p3, p4, p5, p6])], True,
          "no emulator check yet raises either warning (the scenarios start with both granted). JVM tests cover the detector.")


# ============================================================ BOARD 6: Was that you? (defaults)
def confirm_page(problem, label=None, T=LIGHT):
    pr = P[problem]
    app_line = ""
    if label and pr.severity != "Urgent":
        app_line = bd(T, X.Home.CONFIRM_APP.replace("%s", "“" + label + "”"), 17, True)
    return (hd(T, pr.for_mom, 25) + app_line + btn(T, X.Home.CONFIRM_YES) + quiet(T, X.Home.CONFIRM_NO) + bd(T, X.Home.CONFIRM_NO_HELP, 15, True))


def board_drift():
    L, sd = LIGHT, SYS
    bc = P["BrowserChanged"]
    q1 = phone(1, "Home asks", mark(L, "tap") + hdc(L, X.Home.NEEDS_ONE, 27) + bd(L, bc.for_mom, 17, True) + btn(L, X.Home.FIX_MY_PHONE) + quiet(L, X.Home.CALL_A_GUARDIAN) + nav(L, "Home"),
               "Aegida saved who the default apps were when setup finished. A change afterwards is a question, not an accusation.")
    q2 = phone(2, "Was that you? (browser)", confirm_page("BrowserChanged"), "'Fix my phone' opens this page. No app name here, because these four kinds are about a role, not one app.")
    q3 = phone(3, "'Yes, that was me'", mark(L, "good") + hdc(L, X.Home.ALL_GOOD, 27) + bd(L, X.Home.NO_KNOWN_PROBLEMS, 17, True) + quiet(L, X.Home.CALL_A_GUARDIAN) + nav(L, "Home"),
               "Aegida takes the phone as it is now as the new normal and the question is gone. A guardian who was never told about it hears nothing (default-app changes are never sent to a guardian).")
    q4 = phone(4, "'No, show me'", android(sd, "Default apps", sysrow(sd, "Browser app", "Some other browser") + sysrow(sd, "Phone app") + sysrow(sd, "SMS app") + sysrow(sd, "Digital assistant app"), "Opens the right Android screen so she can change it back. The question stays until the setting is as it was, or she says yes."),
               "'If you did not do this, change it back, or call your guardian.' is printed under the buttons.", T=sd, header_on=False)
    q5 = panel("Who is told", ["These four kinds (web browser, text-message app, phone app, assistant app) are shown to her only.", "No guardian message is written for them, so there is nothing to follow up either.", "History still records them: \"%s\"." % bc.what], w=300)
    r1 = row([q1, q2, q3, q4], label="A. THE WEB BROWSER CHANGED")
    r1 = r1.replace("</div></div>", "</div>" + GAP + q5 + "</div>", 1) if False else r1
    sms = phone(1, "Text-message app", confirm_page("SmsAppChanged"), "Was the default app for text messages.")
    dial = phone(2, "Phone app", confirm_page("DialerChanged"), "Was the default app for phone calls.")
    asst = phone(3, "Assistant app", confirm_page("AssistantChanged"), "Was the app that answers 'help' requests.")
    hist = phone(4, "History", hd(L, X.History.TITLE, 25) + tag(L, X.History.TODAY) + hist_line(L, False, P["AssistantChanged"].what, OUTCOME["OwnerWarned"], "3:20 pm")
                 + hist_line(L, False, P["SmsAppChanged"].what, OUTCOME["OwnerWarned"], "2:02 pm") + hist_line(L, False, bc.what, OUTCOME["OwnerWarned"], "9:41 am") + small(L, X.History.KEPT) + nav(L, "History"),
                 "No guardian line on these: nobody was told. They stay amber until she answers.")
    r2 = row([sms, dial, asst, hist, q5], arrows=False, label="B. THE OTHER THREE LOOK THE SAME")
    board("Drift.dc.html", "Was that you? Default apps", "SCENARIO 7 · \"WAS THAT YOU?\" (DEFAULT APPS)", "A default app changed",
          "The web browser, text-message app, phone app or assistant app was changed after setup. Aegida asks. Her answer is one of two big buttons.",
          [r1, r2], True,
          "`emulator-checks.sh drift` covers notification access and screen control only. The build plan says default browser/text/phone changes are not yet walked on the emulator (they need fixtures): no emulator check yet.")


# ============================================================ BOARD 7: Was that you? (access given) + answer
def board_grants():
    L, sd = LIGHT, SYS
    c1 = phone(1, "Notification access given", confirm_page("ListenerAdded", "Cleaner Plus"), "An app was given the right to read notifications. Urgent: Aegida never prints the app's own name here (an attacker can choose it), and says nothing reassuring.")
    c2 = phone(2, "Screen control given", confirm_page("AccessibilityAdded", "Cleaner Plus"), "An app can see and press things on the screen. Urgent.")
    c3 = phone(3, "Device administrator", confirm_page("AdminAdded", "Cleaner Plus"), "An app was given special control. Urgent.")
    c4 = phone(4, "The guardian is told", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, guardian_text("ListenerAdded"), True) + ntfy(GUARD_T, OWNER, guardian_text("AccessibilityAdded"), True)
               + ntfy(GUARD_T, OWNER, guardian_text("AdminAdded"), True), "All three are urgent. 'Aegida asked her if that was her' is the first sentence she cannot yet answer for herself.", T=GUARD_T, header_on=False)
    c5 = phone(5, "'No, show me'", android(sd, "Notification access / Accessibility / Device admin apps", sysrow(sd, "Cleaner Plus", "", toggle(sd, True)) + sysrow(sd, "Aegida", "", toggle(sd, True)), "Opens the Android page for that kind of access (notification access, accessibility, or device administrators) so she can switch it off."),
               "The question stays until the access is gone or she says it was hers.", T=sd, header_on=False)
    r1 = row([c1, c2, c3, c4, c5], arrows=False, label="A. AN APP WAS GIVEN MORE POWER (ALL URGENT)")
    d1 = phone(1, "'Yes, that was me'", confirm_page("ListenerAdded", "Cleaner Plus"), "She taps the first button.")
    d2 = phone(2, "Calm kinds: guardian hears", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, gt(X.Guardian.CONFIRMED))
               + small(GUARD_T, "For a new app from outside the Play Store, only if Sam was told about it first."), "Wording for the calm kind: it says there is nothing more to do.", T=GUARD_T, header_on=False)
    d3 = phone(3, "Urgent kinds: guardian hears", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, gt(X.Guardian.CONFIRMED_URGENT), True)
               + small(GUARD_T, "Because a caller could have told her to say yes, it never says everything is fine."), "For access and remote-control kinds the message ends with 'please call her' if Sam was not expecting it.", T=GUARD_T, header_on=False)
    d4 = panel("Rules for this follow-up", ["Only a guardian who was told about the problem hears that she said it was hers.", "Nothing is sent when alerts are switched off.", "A plain 'Got it' on a scam warning sends nothing.", "The follow-up wording is the same words every time; Aegida cannot add anything else."], w=300)
    r2 = row([d1, d2, d3, d4], arrows=False, label="B. 'YES, THAT WAS ME': WHAT THE GUARDIAN THEN HEARS")
    board("Grants.dc.html", "Was that you? Access given to an app", "SCENARIO 7 (CONTINUED) · \"WAS THAT YOU?\" (ACCESS)", "An app was given more power",
          "Notification access, screen control or device-administrator rights given to an app after setup. These are urgent: her guardian is told at once, and her 'yes' is passed on in different words for urgent and calm kinds.",
          [r1, r2], True,
          "`emulator-checks.sh drift` (notification access, screen control; banner shown, cleared when removed) and `emulator_live.py` steps 2 and 3 (the follow-up wording). No emulator check yet for device administrators.")


# ============================================================ BOARD 8: apps
def board_apps():
    L, sd = LIGHT, SYS
    n, r, s = P["NewAppOutsidePlay"], P["RemoteControlApp"], P["StalkerwareApp"]
    n1 = phone(1, "New app from outside Play", confirm_page("NewAppOutsidePlay", "Free Wallpapers"), "An app installed by hand since setup (not from the Play Store). The app's own name appears in quotes, cut to 40 characters.")
    n2 = phone(2, "The guardian is told", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, guardian_text("NewAppOutsidePlay")), "Not urgent. After 'Yes, that was me' Sam gets the calm follow-up.", T=GUARD_T, header_on=False)
    r1 = phone(3, "Remote-control app", confirm_page("RemoteControlApp", "AnyDesk"), "AnyDesk and similar are matched by package name on a short list. Urgent, so the app's name is not printed.")
    r2 = phone(4, "The guardian is told", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, guardian_text("RemoteControlApp"), True), "Urgent priority. If she then says it was hers, Sam gets the 'please call her' version.", T=GUARD_T, header_on=False)
    sp1 = phone(5, "Known spying app", mark(L, "tap") + hdc(L, X.Home.NEEDS_ONE, 27) + bd(L, s.for_mom, 17, True) + btn(L, X.Home.FIX_MY_PHONE) + quiet(L, X.Home.CALL_A_GUARDIAN) + nav(L, "Home"),
                "On the Echap stalkerware list. There is no 'was that you?': she is told what it is and taken to the app.")
    sp2 = phone(6, "Android's app page", android(sd, "App info", sysrow(sd, "Phone Tracker", "Installed app") + sysrow(sd, "Uninstall", "", ""), "'Fix my phone' opens Android's page for that app so she can remove it."),
                "The warning clears at the next check once the app is gone.", T=sd, header_on=False)
    sp3 = phone(7, "The guardian is told", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, guardian_text("StalkerwareApp"), True), "Urgent. The app is not named in the message.", T=GUARD_T, header_on=False)
    board("Remote.dc.html", "New apps, remote-control apps and spying apps", "SCENARIO 8 · APPS", "A new app, a remote-control app, a known spying app",
          "Three kinds of app Aegida notices after setup. The first two ask 'Was that you?'. The third is on the list of known spying apps and goes straight to removal.",
          [row([n1, n2, r1, r2], arrows=False, label="A. NEW APP · B. REMOTE-CONTROL APP"), row([sp1, sp2, sp3], label="C. KNOWN SPYING APP (ECHAP LIST)")], True,
          "`emulator-checks.sh new-apps` (app installed by hand, AnyDesk fixture), `stalkerware` (a known spying package from the live feed), and `emulator_live.py` steps 2 to 4.")


# ============================================================ BOARD 9: guardian messages + plumbing
def board_guardian():
    L = LIGHT
    sd = SYS
    m1 = phone(1, "Alerts go on: welcome", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, gt(X.Guardian.SET_UP)), "Sent once when she says yes to alerts (setup or My settings) and again for a new guardian. If it cannot be sent, the daily check tries again.", T=GUARD_T, header_on=False)
    m2 = phone(2, "Has not checked in", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, gt(X.Guardian.NOT_CHECKED_IN)),
               "A dead-man's switch. After every daily check Aegida schedules this message for 2 days later, replacing the one waiting. While her phone checks in daily it never arrives. If the phone is off or asleep for 2 days, it is delivered.", T=GUARD_T, header_on=False)
    m3 = phone(3, "One-day reminder", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, gt(X.Guardian.STILL_OPEN)),
               "A warning still not dealt with after 24 hours reminds the same guardian once. The warning stays on Home meanwhile.", T=GUARD_T, header_on=False)
    m4 = panel("Repeat back-off", ["A repeat of the same kind of warning still warns her every time.", "Her guardian is messaged again only after 1 day, then 2, then 4, then 7 days since the last message about that kind.", "Urgent problems are never held back.", "Switching alerts off cancels the waiting 'hasn't checked in' message and sends nothing more."], w=300)
    m5 = phone(5, "Could not be sent: kept", tag(sd, "WHAT AEGIDA DOES, NOT WHAT SHE SEES") + card(sd, bd(sd, "The message is kept in a small outbox on the phone and retried at the next check, with growing waits. She is still warned right away. History says '%s'." % OUTCOME["GuardianNotReached"], 15))
               + small(sd, "After many tries the line reads '%s'." % OUTCOME["GuardianNeverReached"]), "Offline, killed mid-way, or the guardian's service down: nothing is lost.", T=sd, header_on=False)
    m6 = phone(6, "After a day: call", mark(L, "tap") + hdc(L, X.Home.NEEDS_ONE, 27) + bd(L, P["GuardianUnreachable"].for_mom, 17, True) + btn(L, X.Home.FIX_MY_PHONE) + quiet(L, X.Home.CALL_A_GUARDIAN) + nav(L, "Home"),
               "There is no separate Call button on this problem: 'Fix my phone' opens the dialer with the guardian's number (and 'Call a guardian' does the same). If she gave no number, the dialer opens empty.")
    r1 = row([m1, m2, m3, m4, m5, m6], arrows=False, label="A. WHAT A GUARDIAN RECEIVES, AND WHEN")
    c1 = phone(1, "My settings", hd(L, X.Settings.TITLE, 25) + card(L, '<div style="font-size:17px;font-weight:700">%s</div><div style="font-size:15px">%s</div><div style="font-size:17px;font-weight:700">%s</div><div style="font-size:15px">%s</div>%s'
               % (e(X.Settings.NAME), OWNER, e(X.Settings.GUARDIAN), GUARD, quiet(L, X.Settings.CHANGE_GUARDIAN))) + nav(L, "Settings"),
               "My settings > '%s'." % X.Settings.CHANGE_GUARDIAN)
    c2 = phone(2, "Pick a new guardian", hd(L, X.Settings.CHANGE_GUARDIAN_TITLE, 24) + bd(L, X.Setup.GUARDIAN_BODY, 14) + field(L, X.Setup.GUARDIAN_NAME, "Ann") + field(L, X.Setup.GUARDIAN_EMAIL, "ann@example.org")
               + field(L, X.Setup.GUARDIAN_PHONE, "") + btn(L, X.Common.SAVE), "Same three fields as setup.")
    pop = ('<div style="margin:auto 0;background:%s;border-radius:22px;padding:16px;display:flex;flex-direction:column;gap:10px;box-shadow:0 4px 20px rgba(0,0,0,.3)">%s%s<div style="display:flex;justify-content:flex-end;gap:18px;font-weight:700;font-size:17px;color:%s"><span>%s</span><span>%s</span></div></div>'
           % (L["surface"], hd(L, X.Settings.CHANGE_GUARDIAN_POPUP_TITLE.replace("%s", GUARD), 21), bd(L, X.Settings.CHANGE_GUARDIAN_POPUP_BODY.replace("%s", GUARD), 15), L["prim"], X.Settings.CHANGE_GUARDIAN_POPUP_BACK, X.Settings.CHANGE_GUARDIAN_POPUP_OK))
    c3 = phone(3, "The popup", pop, "Before saving she is asked to tell the old guardian herself. Only when there was a guardian to replace.")
    c4 = phone(4, "The old guardian", tag(GUARD_T, "SAM'S PHONE · NTFY") + ntfy(GUARD_T, OWNER, gt(X.Guardian.CHANGED)), "Sent first, with alerts on only. After it, his link stops working and his 'hasn't checked in' message is cancelled. One attempt, no retry.", T=GUARD_T, header_on=False)
    em = (tag(GUARD_T, "ANN'S EMAIL") + card(GUARD_T, '<div style="font-size:14px;font-weight:700">%s</div><div style="font-size:12px;line-height:1.3">%s</div>'
          % (e(X.Invite.SUBJECT.replace("%s", OWNER)), e(X.Invite.BODY.replace("%s", "Ann", 1).replace("%s", "aegida_yyyyyyyy", 2).replace("%s", OWNER, 1)))))
    c5 = phone(5, "The new guardian", em, "Her email app opens with the same invite as in setup and a new private topic. When Ann subscribes she gets the welcome message ('%s')." % gt(X.Guardian.SET_UP), T=GUARD_T, header_on=False)
    r2 = row([c1, c2, c3, c4, c5], label="B. CHANGING THE GUARDIAN")
    board("Guardian.dc.html", "Guardian messages and plumbing", "SCENARIO 9 · WHAT THE GUARDIAN HEARS", "Guardian messages, retries and changing the guardian",
          "Every message is written in her name (title: her first name), carries no link and no message text, and is sent only if she said yes to alerts.",
          [r1, r2], True,
          "`emulator-checks.sh guardian-offline` (kept, retried, reported after a day) and `emulator_live.py` steps 1, 5, 6, 7 and 8 (heartbeat replaced by each check, back-off, guardian change, alerts off). No emulator check yet for the one-day reminder (JVM fake-clock tests only).",
          W=2200)


# ============================================================ BOARD 10: History and settings
def board_history_settings():
    L, D = LIGHT, DARK
    def hist(T, rows):
        return hd(T, X.History.TITLE, 25) + rows + small(T, X.History.KEPT) + nav(T, "History")
    rows = (tag(L, X.History.TODAY) + hist_line(L, False, P["NewAppOutsidePlay"].what, OUTCOME["OwnerWarned"] + " " + OUTCOME["GuardianTold"], "2:14 pm")
            + hist_line(L, True, P["SiteNotifications"].what, OUTCOME["OwnerWarned"] + " " + OUTCOME["GuardianTold"], "9:41 am")
            + tag(L, X.History.YESTERDAY) + hist_line(L, True, P["ListenerOff"].what, OUTCOME["OwnerWarned"] + " " + OUTCOME["GuardianTold"] + " " + OUTCOME["Fixed"], "4:10 pm"))
    h1 = phone(1, "History", hist(L, rows), "Newest first, grouped by day. Green dot = handled, amber dot = still needs her. One event is one line, even though it had a warning and a guardian message.")
    rowsD = rows.replace(L["fg"], D["fg"])
    h2 = phone(2, "History, dark", hd(D, X.History.TITLE, 25) + tag(D, X.History.TODAY) + hist_line(D, False, P["NewAppOutsidePlay"].what, OUTCOME["OwnerWarned"] + " " + OUTCOME["GuardianTold"], "2:14 pm")
               + hist_line(D, True, P["SiteNotifications"].what, OUTCOME["OwnerWarned"] + " " + OUTCOME["GuardianTold"], "9:41 am") + small(D, X.History.KEPT) + nav(D, "History"), "Same screen in the dark theme.", T=D)
    h3 = phone(3, "History, empty", hd(L, X.History.TITLE, 25) + bd(L, X.History.EMPTY, 17) + nav(L, "History"), "Before anything has happened.")
    ps = lambda T: (hd(T, X.Settings.TITLE, 25)
                    + card(T, '<div style="font-size:17px;font-weight:700">%s</div><div style="font-size:15px;color:%s">%s</div><div style="font-size:17px;font-weight:700">%s</div><div style="font-size:15px;color:%s">%s</div>%s'
                           % (e(X.Settings.NAME), T["var"], OWNER, e(X.Settings.GUARDIAN), T["var"], GUARD, quiet(T, X.Settings.CHANGE_GUARDIAN)))
                    + card(T, '<div style="display:flex;align-items:center;gap:10px">%s<div><div style="font-size:17px;font-weight:700">%s</div><div style="font-size:15px">Sunflower</div></div></div>%s' % (tile(46), e(X.Settings.PICTURE), quiet(T, X.Settings.CHANGE)))
                    + card(T, '<div style="display:flex;align-items:center;gap:10px"><div style="flex:1;font-size:17px;font-weight:700">%s</div>%s</div>' % (e(X.Settings.TELL_GUARDIANS), toggle(T, True))))
    s1 = phone(4, "My settings (top)", ps(L) + nav(L, "Settings"), "Name, guardian, picture (with her tile), and the alerts switch. The screen scrolls.")
    s2 = phone(5, "My settings (below)", card(L, '<div style="font-size:17px;font-weight:700">%s</div><div style="font-size:15px;color:%s">%s</div>%s' % (e(X.Settings.WEBSITE_PROTECTION), L["var"], X.Settings.OFF, quiet(L, X.Setup.FILTER_TITLE)))
               + card(L, '<div style="font-size:17px;font-weight:700">%s</div><div style="font-size:14px;color:%s;line-height:1.3">%s</div>' % (e(X.Settings.CREDITS_TITLE), L["var"], e(X.Settings.CREDITS))) + nav(L, "Settings"),
               "Website protection says On or Off from what Aegida read. 'Block scam websites' appears only while it is off. 'Where the lists come from' credits both public lists.")
    s3 = phone(6, "My settings, dark", ps(D) + nav(D, "Settings"), "Dark theme.", T=D)
    board("History.dc.html", "History and My settings", "SCENARIO 10 · HISTORY AND MY SETTINGS", "History and My settings",
          "History keeps 90 days on the phone. My settings is where she changes her name, guardian, picture, the alerts switch and website protection. The tab bar (Home, History, My settings) is the same on every main screen.",
          [row([h1, h2, h3], arrows=False), row([s1, s2, s3], arrows=False)], True,
          "`emulator_live.py` steps 7 and 8 drive My settings (Pick a different guardian, the alerts switch). `emulator-checks.sh shots` photographs Home only; no emulator check yet for History.")


# ============================================================ BOARD 11: banners
def board_banners():
    L, D, sd = LIGHT, DARK, SYS
    ban = lambda T, p: banner(T, P[p].for_mom)
    b1 = phone(1, "On the lock screen", tag(sd, "HER PHONE · NOTIFICATIONS") + ban(sd, "SiteNotifications") + ban(sd, "ScamLink")
               + other_notif(sd, "Messages", "Linda", "See you Sunday for lunch!"),
               "Title is always 'Aegida'. The large icon is her picture's tile with her picture faint and the teal shield on top, so a banner without her picture did not come from her Aegida.", T=sd, header_on=False)
    b2 = phone(2, "Dark theme", tag(DARK, "HER PHONE · NOTIFICATIONS") + banner(DARK, P["WebsiteFilterOff"].for_mom) + banner(DARK, P["StalkerwareApp"].for_mom), "Same banner in the phone's dark theme.", T=dict(DARK, bg="#0E0E0D"), header_on=False)
    b3 = phone(3, "The always-on notice", tag(sd, "HER PHONE · NOTIFICATIONS") + ('<div style="background:%s;border:1px solid %s;border-radius:18px;padding:10px;display:flex;gap:10px;align-items:center">%s<div><div style="font-size:15px;font-weight:700">%s</div><div style="font-size:14px">%s</div></div></div>' % (sd["surface"], sd["line"], shield(30), X.APP_NAME, X.STATUS_NOTICE))
               + small(sd, "Quiet channel '%s'. It says Aegida is running; it carries no warning." % X.CHANNEL_STATUS),
               "Android needs this notice while Aegida watches in the background. Warnings use the louder channel '%s'." % X.CHANNEL_NEEDS_TAP, T=sd, header_on=False)
    b4 = panel("What a banner never does", ["No red. Warnings use the calm Aegida look.", "No full-screen takeover and no overlay on other apps.", "The text is Problem.forMom only: no link and no message text.", "Tap to open Aegida; the banner clears itself."], w=300)
    board("Banners.dc.html", "Banners on the phone", "SCENARIO 11 · BANNERS", "How a warning banner looks",
          "Every warning the owner needs to see is a heads-up banner titled 'Aegida', with her picture and the shield as the large icon. The words are the same Problem sentences shown on Home.",
          [row([b1, b2, b3, b4], arrows=False)], True,
          "no emulator check yet looks at the banner picture (the build plan says it has not been seen on the emulator or a phone). `emulator-checks.sh scenarios` checks that banners are posted, not how they look.")


# ============================================================ BOARD 12: planned
def board_planned():
    board("Planned.dc.html", "Planned, not built yet", "SCENARIO 12 · PLANNED", "Planned, not built yet",
          "Nothing on this board works in the app today. These are ideas the project may build later (build plan phases 5 and Later). They are drawn as notes, not as screens.",
          [row([
              panel("Samsung-specific checks", ["Never-sleeping list: ask Samsung to leave Aegida awake so the daily check cannot be put to sleep.", "Auto Blocker readout: Aegida cannot read this switch today. The first-run step takes her word ('I turned it on').", "Needs an unmanaged Galaxy on One UI 6+ (build plan, phase 5)."], planned=True, w=340, h=420),
              panel("A second guardian", ["Today there is one guardian. A second guardian would hear the same messages, and a 24-hour step could tell the second if the first does not act.", "Needs its own setup step."], planned=True, w=340, h=420),
              panel("Call screening", ["An opt-in way to warn her about a suspicious caller during a call.", "No design yet. Aegida may not use call recording or an accessibility service."], planned=True, w=340, h=420),
              panel("Weekly digest", ["A short weekly message to her guardian summarising what Aegida did.", "Not designed yet."], planned=True, w=340, h=420),
              panel("Also listed as later", ["Remote-access and banking warning.", "A manual 'Check this' button."], planned=True, w=340, h=420),
          ], arrows=False)], False,
          "see docs/build-plan.md (phase 5 and 'Later'). Mention of 'Samsung' in the real first-run flow is the built Auto Blocker step on board 1.")


# ============================================================ overview + canvas
def board_main(entries):
    def sc(n, t, d):
        return ('<div style="display:flex;gap:16px;align-items:flex-start"><div style="flex:none;width:38px;height:38px;border-radius:19px;background:#0F6B73;color:#fff;font-weight:700;font-size:20px;display:flex;align-items:center;justify-content:center">%s</div>'
                '<div><div style="font-weight:700;font-size:21px">%s</div><div style="font-size:17px;line-height:1.35;color:#3E4C59;margin-top:2px">%s</div></div></div>' % (n, e(t), e(d)))
    lines = "".join(sc(*x) for x in entries)
    H = 160 + 90 * len(entries) + 420
    page = f'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Storyboard Overview</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link href="https://fonts.googleapis.com/css2?family=Atkinson+Hyperlegible:wght@400;700&display=swap" rel="stylesheet">
<style>
body{{margin:0}}
</style>
</helmet>
<div style="width: 1280px; height: {H}px; box-sizing: border-box; padding: 64px; background: #EAF0F3; {FONT}; color: #1B2733; display: flex; flex-direction: column; gap: 28px">
<div style="display: flex; align-items: center; gap: 24px">
{shield(84)}
<div style="display: flex; flex-direction: column; gap: 6px">
<div style="font-size: 15px; font-weight: 700; letter-spacing: 0.08em; color: #52606D">STORYBOARD OF THE APP AS BUILT · OCT 8, 2026</div>
<h1 style="margin: 0; font-size: 48px; line-height: 1.05">Aegida</h1>
<div style="font-size: 22px; color: #3E4C59">Every board shows what she sees and what her guardian receives, in the words the code uses.</div>
</div>
</div>
<div style="background:#fff;border:1px solid #D5DEE5;border-radius:20px;padding:26px;display:flex;flex-direction:column;gap:18px">
<div style="font-size:15px;font-weight:700;letter-spacing:.08em;color:#0F6B73">THE BOARDS (ON THE CANVAS TO THE RIGHT)</div>
{lines}
</div>
<div style="background:#fff;border:1px solid #D5DEE5;border-radius:20px;padding:26px;display:flex;flex-direction:column;gap:10px">
<div style="font-size:15px;font-weight:700;letter-spacing:.08em;color:#0F6B73">HOW TO READ THEM</div>
<div style="font-size:18px;line-height:1.4">Phone frames on cream are Aegida's own screens (light theme; dark where shown). Grey frames are other apps or Android's own Settings. Grey-blue frames are a guardian's phone. Each board ends with Built or Planned and the emulator check that verifies it.</div>
<div style="font-size:18px;line-height:1.4">Look and feel: teal shield, cream and dark themes, big Atkinson Hyperlegible type, her safety picture in the header. Never red. The app only ever says "No known problems found."</div>
<div style="font-size:18px;line-height:1.4">The words on these boards are read from Texts.kt and Problem.kt by design/storyboard/generators/gen_v2.py. See docs/storyboard-status.md for the table and the wording differences from the old storyboard.</div>
</div>
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":1280,"height":{H}}}}}'>
class Component extends DCLogic {{
renderVals() {{
return {{}};
}}
}}
</script>
</body>
</html>
'''
    with open(os.path.join(OUT, "Main.dc.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write(page)
    return H


def write_canvas(main_h):
    p = os.path.join(OUT, "canvas.json")
    d = json.load(open(p, encoding="utf-8"))
    logo = d["boards"]["Logo.dc.html"]
    boards = {"Main.dc.html": {"x": 0, "y": 0, "w": 1280, "h": main_h, "title": "Overview"}}
    y = 0
    for i, (fname, title, H, W) in enumerate(BOARDS):
        boards[fname] = {"x": 1360, "y": y, "w": W, "h": H, "title": "%d · %s" % (i + 1, title)}
        y += H + 120
    logo = dict(logo)
    logo["y"] = y
    boards["Logo.dc.html"] = logo
    d["boards"] = boards
    d["order"] = list(boards.keys())
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(d, f, indent=2)


if __name__ == "__main__":
    board_setup(); board_home(); board_popup(); board_links(); board_filter(); board_access()
    board_drift(); board_grants(); board_apps(); board_guardian(); board_history_settings(); board_banners(); board_planned()
    entries = [(i + 1, t, s) for i, (f, t, s) in enumerate([
        ("Setup.dc.html", "First run", "Welcome, then six steps (seven on Samsung): about you and your guardian, safety picture, the invite email, alerts, notification access, website protection, All set."),
        ("App.dc.html", "Home", "Nothing wrong, one thing needs a tap, or could not check."),
        ("PopupGuard.dc.html", "Fake virus pop-ups from a website", "Hidden, she is warned, one switch, her guardian is told."),
        ("ScamText.dc.html", "A message with a link", "Listed scam link and look-alike company link; what it does not flag."),
        ("Filter.dc.html", "Website protection turned off", "The guided fix to Private DNS."),
        ("Access.dc.html", "Lost notification access", "Aegida cannot watch, or cannot warn."),
        ("Drift.dc.html", "Was that you? Default apps", "Browser, text-message, phone and assistant app changed."),
        ("Grants.dc.html", "Was that you? Access given", "Notification access, screen control, device administrator; what the guardian hears after 'Yes'."),
        ("Remote.dc.html", "New, remote-control and spying apps", "Outside Play, AnyDesk and similar, the Echap list."),
        ("Guardian.dc.html", "Guardian messages and plumbing", "Welcome, no check-in, reminder, back-off, retry, changing the guardian."),
        ("History.dc.html", "History and My settings", "The lines, dots and settings."),
        ("Banners.dc.html", "Banners on the phone", "Her picture over the shield as the large icon."),
        ("Planned.dc.html", "Planned, not built", "Samsung checks, second guardian, call screening, weekly digest."),
    ])]
    mh = board_main(entries)
    write_canvas(mh)
    print("wrote", len(BOARDS), "boards")
