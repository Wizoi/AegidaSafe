OUT="/tmp/claude-0/-home-claude/8617c179-4631-55c4-9637-a311de82b96f/scratchpad/storyboard/project/Main.dc.html"
H=1500
def sc(n,t,d):
    return f'''<div style="display: flex; gap: 16px; align-items: flex-start">
<div style="flex: none; width: 38px; height: 38px; border-radius: 19px; background: #0F6B73; color: #fff; font-weight: 700; font-size: 20px; display: flex; align-items: center; justify-content: center">{n}</div>
<div><div style="font-weight: 700; font-size: 22px">{t}</div><div style="font-size: 18px; line-height: 1.35; color: #3E4C59; margin-top: 2px">{d}</div></div>
</div>
'''
def li(t,b=False): return f'<div style="font-size: 18px; line-height: 1.35{"; font-weight: 700" if b else ""}">{t}</div>\n'
def box(title,color,items,bg="#fff",bd="#D5DEE5",bw=1):
    return f'<div style="background: {bg}; border: {bw}px solid {bd}; border-radius: 20px; padding: 24px 28px; display: flex; flex-direction: column; gap: 10px">\n<div style="font-size: 15px; font-weight: 700; letter-spacing: 0.08em; color: {color}">{title}</div>\n'+"".join(items)+'</div>\n'
def top(l,t,d):
    return f'''<div style="background: #fff; border: 1px solid #D5DEE5; border-radius: 20px; padding: 24px; display: flex; flex-direction: column; gap: 8px">
<div style="font-size: 15px; font-weight: 700; letter-spacing: 0.08em; color: #0F6B73">{l}</div>
<div style="font-size: 26px; font-weight: 700">{t}</div>
<div style="font-size: 18px; line-height: 1.4; color: #3E4C59">{d}</div>
</div>
'''
scen="".join([
sc(0,"First run","Mom picks her guardians, a safety picture, and emails them the install and alert links. Auto Blocker recommended, website filter on, today's setup saved."),
sc(1,"Using the app","Home screen, a history of what Aegida did, and settings where she can change her guardians and choices."),
sc(2,"Fake virus warnings","The \"your phone is infected\" alerts. Hidden, then switched off at the source in one tap."),
sc(3,"A new app changes her defaults","A \"cleaner\" app grabs the home screen and browser. Noticed, removed, defaults restored."),
sc(4,"A safety switch is turned off","Mom ignores the prompt. Aegida keeps asking, then tells her guardians."),
sc(5,"Remote-control apps","A fake tech-support caller gets her to install AnyDesk or similar. She is told to hang up; guardians get an urgent alert."),
sc(6,"A scam text is caught","Links in message notifications are checked automatically; a website filter backs it up."),
])
swatch='''<div style="background: #fff; border: 1px solid #D5DEE5; border-radius: 20px; padding: 24px 28px; display: flex; flex-direction: column; gap: 14px">
<div style="font-size: 15px; font-weight: 700; letter-spacing: 0.08em; color: #0F6B73">LOOK AND FEEL</div>
<div style="display: flex; gap: 12px">
<div style="flex: 1; display: flex; flex-direction: column; gap: 6px"><div style="height: 44px; border-radius: 12px; background: #0F6B73"></div><div style="font-size: 15px; color: #3E4C59">Angel</div></div>
<div style="flex: 1; display: flex; flex-direction: column; gap: 6px"><div style="height: 44px; border-radius: 12px; background: #1E7A46"></div><div style="font-size: 15px; color: #3E4C59">All good</div></div>
<div style="flex: 1; display: flex; flex-direction: column; gap: 6px"><div style="height: 44px; border-radius: 12px; background: #FFF1D6; border: 2px solid #B45309"></div><div style="font-size: 15px; color: #3E4C59">Needs a tap</div></div>
<div style="flex: 1; display: flex; flex-direction: column; gap: 6px"><div style="height: 44px; border-radius: 12px; background: #F4B400"></div><div style="font-size: 15px; color: #3E4C59">Her picture</div></div>
</div>
<div style="font-size: 17px; line-height: 1.35; color: #3E4C59">Type is Atkinson Hyperlegible, designed for low vision. No red: scam pop-ups use red, so ours never does. Logo ideas are on the last board.</div>
</div>
'''
every=box("ON EVERY SCREEN","#0F6B73",[li("One action per screen. Steps already done are skipped."),li("Her safety picture and \"Aegida\" on every real screen."),li("Calm colors only. Big text. Plain sentences."),li("Aegida opens each Settings page and checks the result itself."),li("\"Call a guardian\" is always there."),li("One rule: it never asks for money, passwords, or codes.",True)])
how=box("HOW IT WORKS","#0F6B73",[
li("<b>Install:</b> from the Google Play closed test track. Mom sets herself up and chooses her guardians."),
li("<b>Strict where it counts:</b> default apps, safety switches, remote-control apps and scam-list matches. Normal new apps pass if Play Protect and the threat lists are clean."),
li("<b>On her phone:</b> heads-up banners. No full-screen takeovers, no overlays."),
li("<b>Guardians are told automatically</b> because she opted in during setup. A persistent notice shows, there is an off switch, and alerts carry only coarse events."),
li("<b>Escalation:</b> an unfixed item alerts Kevin after 24 hours; the second guardian is told if it is still open 24 hours after that."),
li("<b>Scam coverage:</b> fake virus alerts, scam texts and links, remote-control apps, stalkerware list, daily threat-feed checks."),
li("<b>Her control:</b> she can change guardians and settings any time. Guardians are told when she does."),
])
nxt=box("NEXT STEPS","#8A4B00",[
li("1. Ask Google Play Developer Support whether automatic, consented alerts to family are allowed, before any release beyond the closed test."),
li("2. Test on the emulator first and a Pixel for gaps; Samsung checks (Auto Blocker, sleeping apps, Samsung Internet) come last."),
li("3. Build order: link checks on message notifications and the website filter, then extra settings checks, then the stalkerware list."),
],"#FFF1D6","#E0A44A",2)
html=f'''<!doctype html>
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
<div style="width: 1280px; height: {H}px; box-sizing: border-box; padding: 64px; background: #EAF0F3; font-family: 'Atkinson Hyperlegible', system-ui, sans-serif; color: #1B2733; display: flex; flex-direction: column; gap: 34px">

<div style="display: flex; align-items: center; gap: 24px">
<svg viewBox="0 0 48 48" width="84" height="84"><g fill="#F4B400"><circle cx="24" cy="10" r="6"></circle><circle cx="24" cy="38" r="6"></circle><circle cx="10" cy="24" r="6"></circle><circle cx="38" cy="24" r="6"></circle><circle cx="14" cy="14" r="6"></circle><circle cx="34" cy="34" r="6"></circle><circle cx="34" cy="14" r="6"></circle><circle cx="14" cy="34" r="6"></circle></g><circle cx="24" cy="24" r="9" fill="#6B3E1B"></circle></svg>
<div style="display: flex; flex-direction: column; gap: 6px">
<div style="font-size: 15px; font-weight: 700; letter-spacing: 0.08em; color: #52606D">FOR REVIEW · OCT 7, 2026 · MOM'S GALAXY S23+ (ANDROID 16)</div>
<h1 style="margin: 0; font-size: 48px; line-height: 1.05">Aegida</h1>
<div style="font-size: 22px; color: #3E4C59">Storyboard and user scenarios for version 1</div>
</div>
</div>

<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px">
{top("WHO USES IT","Mom","80+, retired. Doesn't reliably follow prompts or warnings. Sees one calm screen: green when all is well, one big button when something needs her.")}
{top("WHO GUARDS","Guardians","Kevin, plus an optional second such as a sibling. Get short automatic alerts on their phones through ntfy. Nothing for them to approve.")}
{top("WHAT IT DOES","The Aegida app","Remembers how the phone should be set up, hides fake warnings, checks apps and links against public threat lists, and walks Mom through each fix.")}
</div>

<div style="display: flex; gap: 28px; align-items: flex-start">

<div style="width: 560px; flex: none; display: flex; flex-direction: column; gap: 20px">
<div style="background: #fff; border: 1px solid #D5DEE5; border-radius: 20px; padding: 28px; display: flex; flex-direction: column; gap: 22px">
<div style="font-size: 15px; font-weight: 700; letter-spacing: 0.08em; color: #0F6B73">THE SCENARIOS (ON THE CANVAS TO THE RIGHT)</div>
{scen}</div>
{swatch}</div>

<div style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 20px">
{every}{how}{nxt}</div>

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
open(OUT,"w").write(html)
import json
p="/tmp/claude-0/-home-claude/8617c179-4631-55c4-9637-a311de82b96f/scratchpad/storyboard/project/canvas.json"
d=json.load(open(p))
d['boards']={
 "Main.dc.html":{"x":0,"y":0,"w":1280,"h":H,"title":"Overview"},
 "Setup.dc.html":{"x":1360,"y":0,"w":2950,"h":1180,"title":"0 · First run"},
 "App.dc.html":{"x":1360,"y":1300,"w":2200,"h":1180,"title":"1 · Using the app"},
 "PopupGuard.dc.html":{"x":1360,"y":2600,"w":2200,"h":1180,"title":"2 · Fake virus warnings"},
 "NewApp.dc.html":{"x":1360,"y":3900,"w":2200,"h":1180,"title":"3 · A new app changes her defaults"},
 "Drift.dc.html":{"x":1360,"y":5200,"w":2200,"h":1180,"title":"4 · A safety switch is turned off"},
 "Remote.dc.html":{"x":1360,"y":6500,"w":2200,"h":1180,"title":"5 · Remote-control apps"},
 "ScamText.dc.html":{"x":1360,"y":7800,"w":2200,"h":1180,"title":"6 · A scam text is caught"},
 "Logo.dc.html":{"x":1360,"y":9100,"w":2200,"h":1180,"title":"Logo ideas"}}
d['order']=list(d['boards'].keys())
d['notes']['t1']['maxW']=4000
d['notes']['t1']['text']='Aegida — storyboard for review'
d['title']='Aegida Storyboard'
json.dump(d,open(p,"w"),indent=2)
