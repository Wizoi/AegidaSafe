TEAL="#0F6B73"; YEL="#F4B400"; BRN="#6B3E1B"; NAVY="#1B2733"
SHIELD="M48 6 L84 19 V46 C84 67 68 83 48 92 C28 83 12 67 12 46 V19 Z"
def petals(cx,cy,r,pr,col,n=8):
    import math
    return "".join(f'<circle cx="{cx+r*math.cos(2*math.pi*i/n):.1f}" cy="{cy+r*math.sin(2*math.pi*i/n):.1f}" r="{pr}" fill="{col}"></circle>' for i in range(n))
def L1(m=TEAL,a=YEL,k=BRN):  # sunflower shield
    return f'<path d="{SHIELD}" fill="{m}"></path>'+petals(48,47,16,8,a)+f'<circle cx="48" cy="47" r="11" fill="{k}"></circle>'
def L2(m=TEAL,a=YEL,k="#fff"):  # A shield
    return f'<path d="{SHIELD}" fill="{m}"></path><path d="M30 71 L48 30 L66 71" fill="none" stroke="{k}" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"></path><path d="M38 58 H58" stroke="{k}" stroke-width="7" stroke-linecap="round"></path><circle cx="48" cy="22" r="7" fill="{a}"></circle>'
def L3(m=TEAL,a=YEL,k=BRN):  # cradle the sun
    return f'<circle cx="48" cy="38" r="20" fill="{a}"></circle><circle cx="48" cy="38" r="9" fill="{k}"></circle><path d="M14 50 C16 78 36 90 48 90 C60 90 80 78 82 50" fill="none" stroke="{m}" stroke-width="10" stroke-linecap="round"></path>'
def L4(m=TEAL,a=YEL,k=BRN):  # keyhole sunflower badge
    return f'<circle cx="48" cy="48" r="42" fill="{m}"></circle>'+petals(48,48,17,8.5,a)+f'<circle cx="48" cy="48" r="12.5" fill="{k}"></circle><circle cx="48" cy="45" r="4.2" fill="{a}"></circle><path d="M45.5 47 L50.5 47 L52 56 L44 56 Z" fill="{a}"></path>'
def L5(m=TEAL,a=YEL,k=BRN):  # porch-light arch
    import math
    rays="".join(f'<line x1="{48+17*math.cos(math.radians(t)):.1f}" y1="{52+17*math.sin(math.radians(t)):.1f}" x2="{48+22*math.cos(math.radians(t)):.1f}" y2="{52+22*math.sin(math.radians(t)):.1f}" stroke="{a}" stroke-width="4" stroke-linecap="round"></line>' for t in range(180,361,30))
    return f'<path d="M18 90 V46 A30 30 0 0 1 78 46 V90 Z" fill="{m}"></path><circle cx="48" cy="60" r="11" fill="{a}"></circle>'+rays.replace("52+","60+").replace('y1="5','y1="5')
def L6(m=TEAL,a=YEL,k="#fff"):  # Ae monogram tile
    return f'<rect x="8" y="8" width="80" height="80" rx="22" fill="{m}"></rect><text x="48" y="66" text-anchor="middle" font-family="Atkinson Hyperlegible, system-ui, sans-serif" font-weight="700" font-size="52" fill="{k}">Æ</text><circle cx="72" cy="24" r="7" fill="{a}"></circle>'
LOGOS=[("Sunflower shield",L1,"A shield with her sunflower at the centre. Ties to the safety picture she chooses.","Clear 'protection' meaning; the petals blur at 24 px."),
("Letter A shield",L2,"A bold A with a small sun above, inside a shield. Reads as the first letter of the name.","Simple and sturdy; looks like many security apps."),
("Cradled sun",L3,"A sun held in a cupped bowl. Care and keeping something precious safe.","Warm and unlike other security logos; less obviously 'security'."),
("Keyhole sunflower",L4,"A round badge with a sunflower whose centre is a keyhole. Warmth plus lock-and-key security.","Distinctive and friendly; the keyhole is small at notification size."),
("Porch-light arch",L5,"A doorway with a light left on. Home, family, someone watching out.","Friendly and distinctive; says 'home' more than 'security'."),
("Æ tile",L6,"The Æ from the name, in a rounded tile with a sun dot.","Unique to the name; the Æ may confuse readers."),
]
def svg(fn,size,**kw):
    return f'<svg viewBox="0 0 96 96" width="{size}" height="{size}" style="flex: none">{fn(**kw)}</svg>'
