import sys; sys.path.insert(0,'/tmp/claude-0/-home-claude/8617c179-4631-55c4-9637-a311de82b96f/scratchpad/gen')
from logos import *
OUT="/tmp/claude-0/-home-claude/8617c179-4631-55c4-9637-a311de82b96f/scratchpad/storyboard/project/Logo.dc.html"
BG="#2B2F36"; W="#fff"
mono={0:dict(m=W,a=BG,k=W),1:dict(m=W,a=BG,k=BG),2:dict(m=W,a=W,k=BG),3:dict(m=W,a=BG,k=BG),4:dict(m=W,a=BG,k=BG),5:dict(m=W,a=BG,k=BG)}
def card(i,n,fn,desc,tr):
    big=svg(fn,150)
    row=f'''<div style="display:flex;gap:18px;align-items:center;margin-top:6px">
<div style="width:72px;height:72px;border-radius:18px;background:#FFF4CC;border:1px solid #E5D9A8;display:flex;align-items:center;justify-content:center">{svg(fn,50)}</div>
<div style="display:flex;gap:10px;align-items:center">{svg(fn,32)}{svg(fn,22)}</div>
<div style="width:46px;height:46px;border-radius:12px;background:{BG};display:flex;align-items:center;justify-content:center">{svg(fn,26,**mono[i])}</div>
<div style="font-size:14px;color:#52606D;line-height:1.25">App icon, small sizes,<br>notification icon</div></div>'''
    return f'''<div style="background:#fff;border:1px solid #D5DEE5;border-radius:20px;padding:24px 28px;display:flex;flex-direction:column;gap:14px">
<div style="display:flex;align-items:center;gap:22px">{big}
<div style="display:flex;flex-direction:column;gap:6px"><div style="font-size:15px;font-weight:700;letter-spacing:.08em;color:#0F6B73">IDEA {i+1}</div><div style="font-size:26px;font-weight:700;line-height:1.1">{n}</div><div style="font-size:40px;font-weight:700;color:#0F6B73;line-height:1">Aegida</div></div></div>
<div style="font-size:18px;line-height:1.35;color:#1B2733">{desc}</div>
<div style="font-size:16px;line-height:1.35;color:#52606D"><b>Trade-off:</b> {tr}</div>
{row}</div>'''
cards="".join(card(i,*l) for i,l in enumerate(LOGOS))
html=f'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Logo ideas</title>
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
<div style="width: 2200px; height: 1180px; box-sizing: border-box; padding: 56px; background: #EAF0F3; font-family: 'Atkinson Hyperlegible', system-ui, sans-serif; color: #1B2733; display: flex; flex-direction: column; gap: 28px">
<div style="display: flex; flex-direction: column; gap: 8px">
<div style="font-size: 15px; font-weight: 700; letter-spacing: 0.08em; color: #0F6B73">BRAND · FIRST LOGO IDEAS</div>
<h1 style="margin: 0; font-size: 44px; line-height: 1.05">Six logo ideas for Aegida</h1>
<div style="font-size: 21px; line-height: 1.35; color: #3E4C59; max-width: 1500px">All use the app's teal and sunflower yellow, and are drawn from scratch. Each is shown as the app icon, at small sizes, and as the single-colour icon Android uses in the notification bar. No red, to stay clear of scam pop-ups.</div>
</div>
<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 22px">
{cards}
</div>
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":2200,"height":1180}}}}'>
class Component extends DCLogic {{
renderVals() {{
return {{}};
}}
}}
</script>
</body>
</html>
'''
open(OUT,"w").write(html)
