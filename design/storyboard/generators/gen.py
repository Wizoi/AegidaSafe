import os
OUT="/tmp/claude-0/-home-claude/8617c179-4631-55c4-9637-a311de82b96f/scratchpad/storyboard/project"
FONT="font-family: 'Atkinson Hyperlegible', system-ui, sans-serif"
def sun(s=40):
    return f'<svg viewBox="0 0 48 48" width="{s}" height="{s}" style="flex: none"><g fill="#F4B400"><circle cx="24" cy="10" r="6"></circle><circle cx="24" cy="38" r="6"></circle><circle cx="10" cy="24" r="6"></circle><circle cx="38" cy="24" r="6"></circle><circle cx="14" cy="14" r="6"></circle><circle cx="34" cy="34" r="6"></circle><circle cx="34" cy="14" r="6"></circle><circle cx="14" cy="34" r="6"></circle></g><circle cx="24" cy="24" r="9" fill="#6B3E1B"></circle></svg>'
def tag(t): return f'<div style="font-size: 14px; font-weight: 700; letter-spacing: 0.06em; color: #52606D">{t}</div>'
def h(t,c="#1B2733"): return f'<div style="font-size: 24px; font-weight: 700; line-height: 1.15; color: {c}">{t}</div>'
def p(t,c="#3E4C59",s=17): return f'<div style="font-size: {s}px; line-height: 1.3; color: {c}">{t}</div>'
def btn(t,bg="#0F6B73",fg="#fff",mt="margin-top: auto;"):
    return f'<button style="{mt} height: 62px; border: 0; border-radius: 16px; background: {bg}; color: {fg}; font-family: inherit; font-weight: 700; font-size: 21px">{t}</button>'
def btn2(t): return f'<button style="height: 54px; border: 2px solid #9AA5B1; border-radius: 16px; background: #fff; color: #1B2733; font-family: inherit; font-weight: 700; font-size: 19px">{t}</button>'
def brand(): return f'<div style="display: flex; align-items: center; gap: 8px">{sun(26)}<div style="font-size: 15px; font-weight: 700; color: #0F6B73">Aegida</div></div>'
def card(inner,bg="#fff",bd="#D5DEE5",bw=1):
    return f'<div style="background: {bg}; border: {bw}px solid {bd}; border-radius: 14px; padding: 12px; display: flex; flex-direction: column; gap: 6px">{inner}</div>'
def notif(app,title,text,kind="ok",ico=None):
    if kind=="bad": bg,bd="#fff","#C0504D"
    elif kind=="ok": bg,bd="#fff","#9AA5B1"
    ic = ico or sun(30)
    return f'<div style="background: {bg}; border: 2px solid {bd}; border-radius: 16px; padding: 10px 12px; display: flex; gap: 10px; align-items: flex-start">{ic}<div style="display:flex;flex-direction:column;gap:2px"><div style="font-size: 13px; color: #52606D; font-weight: 700">{app}</div><div style="font-size: 17px; font-weight: 700; line-height: 1.2">{title}</div><div style="font-size: 15px; line-height: 1.25; color: #3E4C59">{text}</div></div></div>'
def badico(c="#C0504D"): return f'<div style="flex: none; width: 30px; height: 30px; border-radius: 15px; background: {c}; color: #fff; font-weight: 700; display: flex; align-items: center; justify-content: center">!</div>'
def toggle(on=True,ring=False):
    bg = "#0F6B73" if on else "#9AA5B1"
    x = "26px" if on else "2px"
    r = "outline: 4px solid #F4B400; outline-offset: 4px;" if ring else ""
    return f'<div style="flex: none; width: 54px; height: 30px; border-radius: 15px; background: {bg}; position: relative; {r}"><div style="position: absolute; top: 3px; left: {x}; width: 24px; height: 24px; border-radius: 12px; background: #fff"></div></div>'
def row(label,sub="",right=""):
    s = f'<div style="font-size: 14px; color: #52606D">{sub}</div>' if sub else ""
    return f'<div style="display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 10px 0; border-bottom: 1px solid #E4EAEF"><div style="display:flex;flex-direction:column"><div style="font-size: 17px; font-weight: 700">{label}</div>{s}</div>{right}</div>'
def good(t): return f'<div style="background: #DDF1E4; border: 2px solid #1E7A46; border-radius: 14px; padding: 12px; font-size: 18px; font-weight: 700; color: #14532D; line-height: 1.25">{t}</div>'
def warn(t): return f'<div style="background: #FFF1D6; border: 2px solid #B45309; border-radius: 14px; padding: 12px; font-size: 18px; font-weight: 700; color: #7A3E00; line-height: 1.25">{t}</div>'
def check(s=64): return f'<div style="align-self: center; width: {s}px; height: {s}px; border-radius: {s//2}px; background: #1E7A46; display: flex; align-items: center; justify-content: center"><svg width="{s//2}" height="{s//2}" viewBox="0 0 24 24"><path d="M5 12l5 5 9-10" fill="none" stroke="#fff" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"></path></svg></div>'
def phone(n,title,body,cap,bg="#F7FAFB"):
    return f'''<div style="width: 300px; display: flex; flex-direction: column; gap: 14px">
<div style="display: flex; align-items: center; gap: 10px; height: 34px"><div style="width: 34px; height: 34px; border-radius: 17px; background: #0F6B73; color: #fff; font-weight: 700; font-size: 19px; display: flex; align-items: center; justify-content: center">{n}</div><div style="font-weight: 700; font-size: 20px">{title}</div></div>
<div style="width: 300px; height: 600px; box-sizing: border-box; border: 7px solid #1B2733; border-radius: 34px; background: {bg}; overflow: hidden; display: flex; flex-direction: column; padding: 16px; gap: 12px">
{body}
</div>
<div style="font-size: 17px; line-height: 1.35; color: #3E4C59">{cap}</div>
</div>'''
ARROW='<div style="width: 56px; margin-top: 338px; display: flex; justify-content: center"><svg width="32" height="20" viewBox="0 0 32 20"><path d="M2 10H28M20 3l8 7-8 7" fill="none" stroke="#7B8794" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"></path></svg></div>'
def board(fname,title,kicker,head,sub,phones,W=2200):
    ph = ARROW.join(phones)
    html=f'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>{title}</title>
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
<div style="width: {W}px; height: 1180px; box-sizing: border-box; padding: 56px; background: #EAF0F3; {FONT}; color: #1B2733; display: flex; flex-direction: column; gap: 30px">
<div style="display: flex; flex-direction: column; gap: 8px">
<div style="font-size: 15px; font-weight: 700; letter-spacing: 0.08em; color: #0F6B73">{kicker}</div>
<h1 style="margin: 0; font-size: 44px; line-height: 1.05">{head}</h1>
<div style="font-size: 21px; line-height: 1.35; color: #3E4C59; max-width: 1400px">{sub}</div>
</div>
<div style="display: flex; align-items: flex-start">
{ph}
</div>
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{W},"height":1180}}}}'>
class Component extends DCLogic {{
renderVals() {{
return {{}};
}}
}}
</script>
</body>
</html>
'''
    open(os.path.join(OUT,fname),"w").write(html)

# ---------- Scenario 1
shade_bg="#E6EBF0"
s1=[
phone(1,"The fake warning",
 tag("MOM'S PHONE · NOTIFICATIONS")+
 notif("Chrome · from a website","YOUR PHONE IS INFECTED!","3 viruses found. Tap now to clean your phone.","bad",badico())+
 notif("Messages","Linda","See you Sunday for lunch!","ok",'<div style="flex:none;width:30px;height:30px;border-radius:15px;background:#1F5FA8"></div>')+
 notif("Chrome · from a website","YOUR PHONE IS INFECTED!","Virus alert. Tap to remove.","bad",badico())+
 p("It comes back about every minute.","#7A3E00",16),
 "This is the real problem. It is not a virus. A website she once allowed to send notifications is posting fake alarms.", bg=shade_bg),
phone(2,"Angel hides it",
 tag("MOM'S PHONE · NOTIFICATIONS")+
 notif("Aegida","Fake warning hidden","A website was sending fake virus alerts. Tap to stop it for good.","ok")+
 notif("Messages","Linda","See you Sunday for lunch!","ok",'<div style="flex:none;width:30px;height:30px;border-radius:15px;background:#1F5FA8"></div>')+
 p("The scary alert is removed the moment it appears. Her picture marks the one that is really us.","#3E4C59",16),
 "Aegida reads notifications, removes ones that match a scam pattern, and replaces them with one calm card. Real messages are left alone.", bg=shade_bg),
phone(3,"One button",
 brand()+h("A website was sending fake virus alerts.")+p("Nothing is wrong with your phone. I can turn that website off.")+
 card(p("<b>Website:</b> free-prizes-check.example<br><b>Alerts hidden:</b> 14 today","#1B2733",16))+
 btn("Turn it off")+btn2("Not now"),
 "Plain words first: it is not a virus. One big button. If she says Not now, it asks again later instead of arguing."),
phone(4,"Her one tap",
 tag("PHONE SETTINGS · OPENED BY THE HELPER")+h("Notifications from this site")+
 card(row("Allow notifications","free-prizes-check.example",toggle(True,True))+row("Sound","",toggle(True)))+
 warn("Tap the switch with the yellow ring.")+p("Android needs her own tap for this. We open the exact page, so she never hunts through menus.","#52606D",16),
 "The app jumps straight to that one site's switch. No searching, no data wipe, no signing back in to Chrome."),
phone(5,"Checked for her",
 brand()+check()+good("Fixed. That website can't send alerts anymore.")+
 p("I checked the switch myself. If more fake alerts come from somewhere else, I'll tell you.","#3E4C59",18)+
 btn("Done"),
 "The app re-reads the setting to confirm. If the alerts continue, it looks at the next suspect (the Google app was the second source last time)."),
phone(6,"Kevin hears",
 tag("KEVIN'S PHONE · NTFY")+
 notif("ntfy · mom-phone-7QK2","Fake virus alerts stopped","free-prizes-check.example sent 14 in 1 day. Mom switched it off at 3:12 pm. Nothing else needed.","ok",sun(30))+
 notif("ntfy · mom-phone-7QK2","Still getting alerts","Source is now the Google app. Mom hasn't fixed it yet.","ok",sun(30))+
 p("Two example alerts: the quiet 'fixed' one and the one that needs him.","#52606D",16),
 "Kevin gets a short summary either way. The second card shows what he'd see if the first fix did not hold.", bg=shade_bg),
]
board("PopupGuard.dc.html","Fake virus pop-up","SCENARIO 2 · FAKE VIRUS WARNINGS","“Your phone is infected” every minute","It is not a virus. It is website notification spam. Aegida hides each alert and switches the source off in one tap, with no data wipe.",s1)

# ---------- Scenario 2
s2=[
phone(1,"She installs a “cleaner”",
 tag("PLAY STORE · AFTER AN AD")+h("Super Phone Cleaner")+
 card(p("Speed up your phone. Remove viruses. Free!","#1B2733",16)+p("★★★★☆ · 5M+ downloads","#52606D",14))+
 '<div style="height: 54px; border-radius: 27px; background: #1E7A46; color: #fff; font-weight: 700; font-size: 19px; display: flex; align-items: center; justify-content: center">Install</div>'+
 p("She believes the ad. Nothing here is unusual yet.","#52606D",16),
 "An ad says her phone is slow or infected. She installs a lookalike cleaner. Android lets this happen; Aegida can't stop installs."),
phone(2,"It grabs her defaults",
 tag("ANDROID SYSTEM DIALOG")+
 card(h("Set Super Phone Cleaner as your default home app?")+p("It will control your home screen.","#3E4C59",16)+
  '<div style="display:flex;gap:10px;margin-top:6px"><div style="flex:1;height:44px;border-radius:22px;border:2px solid #9AA5B1;display:flex;align-items:center;justify-content:center;font-weight:700">No</div><div style="flex:1;height:44px;border-radius:22px;background:#1F5FA8;color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700">Yes</div></div>')+
 p("She taps Yes to make the pop-up go away.","#52606D",16),
 "The app asks to become Home and Browser. Android's own dialog is easy to accept, so Aegida is the safety net behind it."),
phone(3,"Angel notices",
 tag("MOM'S PHONE · NOTIFICATIONS")+
 notif("Aegida","Something changed","Your home screen was switched by an app called Super Phone Cleaner. Tap to put it back.","ok")+
 p("Within about a minute of the change.","#52606D",16),
 "Strict mode: anything that differs from the saved snapshot counts as a problem, and the app that did it is named when Android allows.", bg=shade_bg),
phone(4,"What happened, in words",
 brand()+h("A new app changed your phone.")+
 card(p("<b>App:</b> Super Phone Cleaner<br><b>Installed:</b> 2 minutes ago, Play Store<br><b>Changed:</b> Home screen, Browser<br><b>On threat lists:</b> Not found","#1B2733",16),"#FFF1D6","#B45309",2)+
 btn("Put my phone back")+btn2("Call Kevin"),
 "Attribution comes from install source and install time. The list is for Kevin's eyes; Mom sees the headline and one button."),
phone(5,"Two taps, guided",
 tag("ANDROID SYSTEM DIALOGS · OPENED BY THE HELPER")+
 card(h("Use One UI Home as your home app?")+'<div style="display:flex;gap:10px;margin-top:6px"><div style="flex:1;height:44px;border-radius:22px;border:2px solid #9AA5B1;display:flex;align-items:center;justify-content:center;font-weight:700">No</div><div style="flex:1;height:44px;border-radius:22px;background:#1F5FA8;color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700;outline:4px solid #F4B400;outline-offset:3px">Yes</div></div>')+
 card(h("Uninstall this app?")+p("Super Phone Cleaner","#3E4C59",16)+'<div style="display:flex;gap:10px;margin-top:6px"><div style="flex:1;height:44px;border-radius:22px;border:2px solid #9AA5B1;display:flex;align-items:center;justify-content:center;font-weight:700">Cancel</div><div style="flex:1;height:44px;border-radius:22px;background:#1F5FA8;color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700;outline:4px solid #F4B400;outline-offset:3px">OK</div></div>')+
 warn("Tap Yes, then OK."),
 "Aegida restores Home and Browser, then asks Android to uninstall the intruder. Android requires her tap for each; the app can't do it silently."),
phone(6,"Kevin is told",
 tag("KEVIN'S PHONE · NTFY")+
 notif("ntfy · mom-phone-7QK2","New app removed","Super Phone Cleaner took Home + Browser at 4:05 pm. Mom removed it; defaults restored.","ok",sun(30))+
 card(p("Threat lists: not found.<br>Nothing for you to approve. Call Mom if you want to hear what she tapped.","#1B2733",16))+
 good("Phone matches the saved setup again."),
 "If an app is on a public threat list (Play Protect, Safe Browsing, MalwareBazaar), the alert says so. Otherwise it is an information-only alert.", bg=shade_bg),
]
board("NewApp.dc.html","A new app takes over her defaults","SCENARIO 3 · A NEW APP CHANGES HER DEFAULTS","An app installs itself as her home screen","This is the strict-baseline case: her home screen, browser, and safety switches are saved as 'known good', so a change is a problem by default, no matter which app caused it.",s2)

# ---------- Scenario 3
s3=[
phone(1,"A switch gets turned off",
 tag("SETTINGS · CONNECTIONS")+h("Private DNS")+
 card(row("Private DNS","Blocks known scam websites",toggle(False))+row("Mode","Off",""))+
 p("A caller on the phone said, 'Just turn this off so I can fix it.' Or she tapped it by accident.","#52606D",16),
 "Scam callers talk people into this. Aegida can't prevent the tap, but it sees the result."),
phone(2,"Angel speaks up",
 tag("MOM'S PHONE · NOTIFICATIONS")+
 notif("Aegida","A safety switch was turned off","Tap to turn it back on. It takes 10 seconds.","ok")+
 p("She swipes it away without reading.","#52606D",16),
 "This is the expected path. She is not known to follow prompts, so the app plans for the swipe.", bg=shade_bg),
phone(3,"It keeps asking",
 tag("MOM'S PHONE · LATER THAT DAY")+
 notif("Aegida","Still turned off","Website protection helps block scam websites. Tap to fix.","ok")+
 notif("Aegida","Please fix when you can","One safety switch is still off.","ok")+
 p("Repeats every few hours. Never red, never scary, never a pop-up over other apps.","#52606D",16),
 "Strict mode nags gently and often, not loudly. It never takes over the screen.", bg=shade_bg),
phone(4,"The app itself",
 brand()+warn("1 thing needs a tap")+p("Website protection is off. It helps block scam websites.","#1B2733",18)+
 btn("Fix my phone")+btn2("Call Kevin"),
 "Whenever she opens Aegida, this is the whole screen. If everything is fine it shows a green check instead."),
phone(5,"Kevin escalates",
 tag("KEVIN'S PHONE · NTFY")+
 notif("ntfy · mom-phone-7QK2","Website protection off for 24 hours","Mom hasn't fixed it after 5 reminders. Turned off at 2:40 pm yesterday. Consider calling her.","ok",sun(30))+
 card(p("Not a call she expected? Ask who phoned her.","#1B2733",16))+
 p("The 24 hours is a setting for Kevin to choose.","#52606D",16),
 "After the delay, Kevin gets a single alert with the reminder count and the time it changed. He phones her; no other action needed.", bg=shade_bg),
phone(6,"Fixed together",
 tag("SETTINGS · OPENED BY THE HELPER")+h("Private DNS")+
 card(row("Private DNS","Blocks known scam websites",toggle(True,True)))+
 warn("Tap the switch with the yellow ring.")+check(44)+good("Fixed. Kevin has been told it is back on."),
 "Aegida re-reads the Private DNS setting and confirms it is back, so this one is verified, not assumed."),
]
board("Drift.dc.html","A safety switch is turned off","SCENARIO 4 · STRICT MODE IN ACTION","She ignores the prompt. The app doesn't.","Mom isn't expected to follow warnings, so the design assumes she won't. Aegida repeats gently, then tells Kevin.",s3)

# ---------- Setup (remote)
bk=open("/tmp/claude-0/-home-claude/8617c179-4631-55c4-9637-a311de82b96f/scratchpad/gen/Setup.backup.html").read()
i0=bk.index('<div style="font-size: 14px; font-weight: 700; letter-spacing: 0.06em; color: #52606D">SETUP · 2 OF 6')
i1=bk.index('This one</button>',i0)+len('This one</button>')
pic=bk[i0:i1].replace("2 OF 6","2 OF 7")
def inp(lbl,val="",ph=""):
    v=f'value="{val}"' if val else f'placeholder="{ph}"'
    return f'<label style="display: flex; flex-direction: column; gap: 4px; font-size: 14px; font-weight: 700; color: #52606D">{lbl}<input {v} style="height: 44px; box-sizing: border-box; border: 2px solid #9AA5B1; border-radius: 12px; background: #fff; padding: 0 12px; font-family: inherit; font-size: 18px; color: #1B2733"></label>'
def mailbox(rows,body):
    return f'<div style="background:#fff;border:1px solid #D5DEE5;border-radius:14px;padding:10px;display:flex;flex-direction:column;gap:6px;font-size:14px;line-height:1.3"><div><b style="color:#52606D">To</b> Kevin, Sam</div><div style="border-top:1px solid #E4EAEF;padding-top:6px"><b style="color:#52606D">Subject</b> Mom has chosen you as her Aegida guardian</div><div style="border-top:1px solid #E4EAEF;padding-top:6px;color:#1B2733">{body}</div></div>'
emailbody="Hi, I've picked you as one of my guardians. You'll get a message if my phone needs help.<br><b>1.</b> Get the free ntfy app:<br><span style='color:#1F5FA8;text-decoration:underline'>Android · iPhone</span><br><b>2.</b> Then tap to receive my alerts:<br><span style='color:#1F5FA8;text-decoration:underline'>ntfy.sh/mom-7QK2…</span>"
su=[
phone(1,"Who's helping?",
 tag("SETUP · 1 OF 7")+h("Choose your guardians")+
 inp("First helper's name","Kevin")+inp("First helper's email","kevin@example.com")+
 inp("Backup helper's name (optional)","Sam")+inp("Backup helper's email","sam@example.com")+
 btn("Next"),
 "Mom enters Kevin, plus an optional backup such as a sibling. Both get every alert. 'Add another' appears after the first backup."),
ARROWPLACEHOLDER if False else None,
]
su=[
phone(1,"Who's helping?",
 tag("SETUP · 1 OF 7")+h("Choose your guardians")+
 inp("Guardian's name","Kevin")+inp("Guardian's email","kevin@example.com")+
 inp("Second guardian (optional)","Sam")+inp("Second guardian's email","sam@example.com")+
 btn("Next"),
 "Mom chooses her own guardians: Kevin, plus an optional second such as a sibling. Both get every alert. She is in charge of who protects her."),
phone(2,"Her safety picture",pic.replace("\n","\n"),
 "She picks it herself. It is reassurance, not the main defence: fakes are also hidden outright."),
phone(3,"Email to guardians",
 tag("SETUP · 3 OF 7")+h("Tell your guardians")+p("Send them a short note so they can get your alerts.","#3E4C59",17)+
 mailbox(0,emailbody)+
 warn("Tap Send in your email.")+
 p("Opens her normal email app with everything filled in. She only presses Send.","#52606D",15),
 "No QR code, no custom setup. The app drafts the invitation in her own mail app, so it comes from her real address and nothing is sent without her tap. It includes ntfy install links for Android and iPhone and her private alert link."),
phone(4,"Guardian's side",
 tag("KEVIN'S PHONE · EMAIL")+
 card(p("<b>From:</b> Mom<br><b>Mom has chosen you as her Aegida guardian</b>","#1B2733",16)+p("1. Get ntfy: <u style='color:#1F5FA8'>Android</u> · <u style='color:#1F5FA8'>iPhone</u><br>2. <u style='color:#1F5FA8'>Tap to receive alerts</u>","#3E4C59",15))+
 notif("ntfy","Subscribed to mom-7QK2","You will see Mom's alerts here.","ok",'<div style="flex:none;width:30px;height:30px;border-radius:8px;background:#317F6F"></div>')+
 notif("ntfy · mom-7QK2","Test alert from Mom's phone","Tap Got it to tell her this works.","ok",sun(30))+
 '<div style="height:44px;border-radius:22px;background:#0F6B73;color:#fff;font-weight:700;display:flex;align-items:center;justify-content:center;font-size:18px">Got it</div>',
 "The guardian installs ntfy from the link (Android or iPhone, so a sibling can be on either), then taps the alert link and is subscribed. The test alert carries a Got it button that replies to her phone (ntfy action buttons; to verify).", bg=shade_bg),
phone(5,"Tell my guardians",
 tag("SETUP · 4 OF 7")+h("Let Aegida tell your guardians about serious problems?")+
 p("Only when something serious happens. They never see your messages, calls or where you are.","#3E4C59",16)+
 notif("Example alert","Mom's phone needs help","A remote-control app was installed at 2:14 pm.","ok",sun(30))+
 card(row("Tell my guardians","You can switch this off any time",toggle(True,True)))+
 btn("Yes, tell them"),
 "Mom opts in herself, once, after seeing exactly what a guardian would receive. After this, alerts go out on their own: no button for her to press. Off switch always in the app."),
phone(6,"Safety switches",
 tag("SETUP · 5 OF 7")+h("Extra protection on your phone")+
 card(row("Auto Blocker","Blocks unknown apps and risky links",toggle(True,True)))+
 warn("Tap the switch with the yellow ring.")+
 p("Recommended. The app opens the page. Android won't let any app switch it on or check it.","#52606D",16)+
 btn("Show me")+btn2("Not now"),
 "Auto Blocker is recommended, not required. Samsung gives apps no way to turn it on or read it, so Aegida only guides one tap, never shows it as verified, and tells guardians if she chose Not now."),
phone(7,"Save today's setup",
 tag("SETUP · 6 OF 7")+h("Saving how your phone is set up")+
 card(row("Browser","Chrome")+row("Home screen","One UI Home")+row("Messages","Samsung Messages")+row("Phone","Phone")+row("Apps","74 saved")+row("Auto Blocker","You turned it on"))+
 good("Your guardians are connected."),
 "This is the 'known good' snapshot. A copy of the summary goes to her guardians so they can sanity-check it."),
phone(8,"All set",
 tag("SETUP · 7 OF 7")+sun(52)+h("All set. Your phone looks good.")+
 good("Aegida is watching quietly.")+notif("Aegida","Protecting your phone","Always on. Tap to open.","ok")+p("I never ask for money, passwords, or codes.","#1B2733",18)+
 btn("Fix my phone")+btn2("Not sure? Call a guardian"),
 "From now on this is the whole app. One big button; green when all is well. A small always-on notice shows that sharing is active (Play requires this)."),
]
board("Setup.dc.html","Setup","SCENARIO 0 · FIRST RUN","Setting up from states away","Mom runs setup herself, with a phone call from Kevin if she needs it. She picks her guardians, and her own mail app sends them the install links and alert link. Then the app saves a snapshot of her phone as 'known good'.",su,W=2950)

# ---------- Scenario 4
s4=[
phone(1,"The scam call",
 tag("INCOMING CALL")+'<div style="margin-top:30px;align-self:center;width:90px;height:90px;border-radius:45px;background:#9AA5B1"></div>'+
 '<div style="text-align:center;font-size:26px;font-weight:700">Unknown number</div>'+
 p("“Hello, this is Microsoft. Your phone has a virus. Install an app so I can fix it for you.”","#3E4C59",18)+
 '<div style="margin-top:auto;display:flex;justify-content:space-around"><div style="width:64px;height:64px;border-radius:32px;background:#9AA5B1"></div><div style="width:64px;height:64px;border-radius:32px;background:#1E7A46"></div></div>',
 "A caller pretends to be tech support and gets her to install a remote-control app. This is the highest-stakes scenario."),
phone(2,"She installs it",
 tag("PLAY STORE")+h("QuickSupport")+card(p("Remote support. Let others see and control your screen.","#1B2733",16))+
 '<div style="height:54px;border-radius:27px;background:#1E7A46;color:#fff;font-weight:700;font-size:19px;display:flex;align-items:center;justify-content:center">Install</div>'+
 p("The app shows a 9-digit code. The caller asks her to read it out.","#52606D",16)+
 card(p("<b>Your code</b><br><span style='font-size:28px;letter-spacing:.1em'>417 922 305</span>","#1B2733",16)),
 "Remote-control apps are legitimate, which is why they work so well for scams. The code is what gives the caller control."),
phone(3,"Angel jumps in",
 tag("MOM'S PHONE · NOTIFICATIONS")+
 notif("Aegida","Stop. Don't read out any numbers.","An app that lets someone control your phone was just installed. Tap to remove it.","ok")+
 p("Calm words, but sent as an important alert that appears over the lock screen.","#52606D",16),
 "Triggered on install, within about a minute. Detection uses a list of known remote-control apps plus a behaviour check.", bg=shade_bg),
phone(4,"One clear message",
 brand()+warn("Someone could be controlling your phone.")+
 p("Hang up if someone is on the phone. Don't read out any numbers.","#1B2733",19)+
 btn("Remove it now")+btn2("Call Kevin"),
 "Tells her what to do with the call, then offers the one fix. 'Call Kevin' dials him directly."),
phone(5,"Removed, then checked",
 tag("ANDROID · OPENED BY PHONE ANGEL")+
 card(h("Uninstall this app?")+p("QuickSupport","#3E4C59",16)+'<div style="display:flex;gap:10px;margin-top:6px"><div style="flex:1;height:44px;border-radius:22px;border:2px solid #9AA5B1;display:flex;align-items:center;justify-content:center;font-weight:700">Cancel</div><div style="flex:1;height:44px;border-radius:22px;background:#1F5FA8;color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700;outline:4px solid #F4B400;outline-offset:3px">OK</div></div>')+
 card(row("Accessibility","QuickSupport is allowed to control your screen",toggle(True,True)))+
 warn("Tap OK. Then turn off the switch with the ring if it's still there."),
 "If the app was also given Accessibility access (the setting that lets it tap for her), the app opens that page too and checks it is off."),
phone(6,"Kevin gets an urgent alert",
 tag("KEVIN'S PHONE · NTFY · URGENT")+
 notif("ntfy · mom-7QK2 · urgent","Remote-control app installed","QuickSupport at 2:14 pm. Likely a scam call. Call Mom now.","bad",badico("#B45309"))+
 notif("ntfy · mom-7QK2","Removed at 2:16 pm","QuickSupport was uninstalled by Mom. Accessibility is off. Phone matches the saved setup.","ok",sun(30))+
 p("Her second guardian gets both alerts too.","#52606D",16),
 "This is the one alert type that should make his phone ring, even on Do Not Disturb. He can set that in the ntfy app.", bg=shade_bg),
]
board("Remote.dc.html","Remote-control app installed","SCENARIO 5 · REMOTE-CONTROL APPS","A “tech support” caller gets her to install a remote app","Aegida spots AnyDesk, TeamViewer QuickSupport, RustDesk, AirDroid, Chrome Remote Desktop and similar apps, plus any app that holds screen-control powers it shouldn't. Mom is told what to do first; her guardians are told at once.",s4)

# ---------- App board (scenario 1)
def nav(active):
    items=["Home","History","My settings"]
    out=""
    for it in items:
        on = it==active
        bg = "#0F6B73" if on else "#fff"; fg="#fff" if on else "#1B2733"
        out+=f'<div style="flex:1;height:48px;border-radius:14px;background:{bg};color:{fg};border:2px solid #0F6B73;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:16px">{it}</div>'
    return f'<div style="margin-top:auto;display:flex;gap:6px">{out}</div>'
def ev(color,title,sub,time):
    dot=f'<div style="flex:none;width:14px;height:14px;border-radius:7px;background:{color};margin-top:5px"></div>'
    return f'<div style="display:flex;gap:10px;padding:8px 0;border-bottom:1px solid #E4EAEF">{dot}<div style="flex:1"><div style="font-size:16px;font-weight:700;line-height:1.2">{title}</div><div style="font-size:14px;color:#52606D;line-height:1.25">{sub}</div></div><div style="font-size:13px;color:#52606D;flex:none">{time}</div></div>'
def day(t): return f'<div style="font-size:13px;font-weight:700;letter-spacing:.06em;color:#0F6B73;margin-top:4px">{t}</div>'
G="#1E7A46"; A="#B45309"
app=[
phone(1,"Open the app: all good",
 brand()+check(72)+h("Your phone looks good.")+p("Checked today at 9:02 am.","#3E4C59",18)+
 good("Everything is as it should be.")+btn2("Call a guardian")+nav("Home"),
 "The whole home screen. Green check, one sentence, her picture beside the name. Nothing to do."),
phone(2,"Open the app: needs a tap",
 brand()+warn("1 thing needs a tap")+p("Website protection is off. It helps block scam websites.","#1B2733",18)+
 btn("Fix my phone",mt="margin-top: 6px;")+btn2("Call a guardian")+nav("Home"),
 "When something needs her, one big button. If there is more than one item, Fix my phone walks through them in order."),
phone(3,"History",
 tag("WHAT PHONE ANGEL DID")+day("TODAY")+
 ev(G,"Hid a fake virus alert","From free-prizes-check.example","12:40 pm")+
 ev(G,"Checked 74 apps","All fine","9:02 am")+
 day("YESTERDAY")+
 ev(G,"Removed Super Phone Cleaner","Home screen put back","4:05 pm")+
 ev(A,"Flagged a scam text","You didn't open the link","11:15 am")+
 ev(G,"Website protection back on","Fixed with your tap","6:40 pm")+nav("History"),
 "A plain list of events, newest first. Green dots are handled; amber ones still need her. Stored on the phone for 90 days."),
phone(4,"One event",
 tag("HISTORY · DETAIL")+h("Removed Super Phone Cleaner")+
 card(p("<b>What happened:</b> This app became your home screen and browser.<br><b>When:</b> Yesterday, 4:05 pm<br><b>Where it came from:</b> Play Store","#1B2733",16))+
 card(p("<b>What I did:</b> Put your home screen back and removed the app, with your OK.<br><b>Your guardians were told</b> at 4:06 pm.","#1B2733",16))+
 good("Fixed.")+btn("Back",mt="margin-top: 6px;"),
 "Tapping an event shows what happened, what was done, and who was told. Same words guardians saw."),
phone(5,"My settings",
 tag("MY SETTINGS")+
 card(row("My guardians","Kevin, Sam")+row("My safety picture","Sunflower")+row("My first name","Not set")+row("Tell my guardians about serious problems","",toggle(True))+row("Website protection","On")+row("About Aegida","Version 1.0"),"#fff","#D5DEE5",1)+nav("My settings"),
 "Everything she entered in setup can be changed here. The guardians switch is the opt-in from setup; she can turn it off."),
phone(6,"Change a guardian",
 tag("MY GUARDIANS")+h("Change Sam")+
 inp("Name","Sam")+inp("Email","sam@example.com")+
 warn("Kevin will be told about this change.")+
 p("The new person gets an email invitation from you with the ntfy links.","#3E4C59",16)+
 btn("Save and send invitation",mt="margin-top: 6px;")+btn2("Remove Sam"),
 "Safety rule: any guardian change tells the existing guardians. A scammer who talks her into adding them is seen at once."),
]
board("App.dc.html","Using the app","SCENARIO 1 · AFTER SETUP","Opening Aegida","What Mom sees when she opens the app: a calm home screen, a history of what Aegida has done, and settings where she can change her guardians and the other choices she made in setup.",app)

# ---------- Scam text board (scenario 6)
st=[
phone(1,"One-time setup",
 tag("OFFERED AT THE END OF SETUP")+h("Block dangerous websites")+
 p("A free safety service that blocks known scam sites. Aegida opens the page and checks it.","#3E4C59",16)+
 card(row("Private DNS","Settings > Connections > More connection settings")+row("Provider name","security.cloudflare-dns.com",toggle(True,True)))+
 warn("Paste the name and tap Save.")+btn("Copy the name",mt="margin-top: 6px;"),
 "Set once, then it works everywhere on the phone, with nothing for her to decide. Rescue step: 'set back to Automatic' if a Wi-Fi sign-in page misbehaves."),
phone(2,"A scam text arrives",
 tag("MOM'S PHONE · NOTIFICATIONS")+
 notif("Messages","+1 (555) 019-2234","USPS: your package is on hold. Pay $1.99 now: usps-redelivery.example/x","bad",badico()),
 "The common 'package on hold' text. The wording and the link are what Aegida reads.", bg=shade_bg),
phone(3,"Aegida steps in",
 tag("MOM'S PHONE · NOTIFICATIONS")+
 notif("Aegida","That text looks like a scam","Don't tap the link. USPS doesn't ask for payment by text.","ok")+
 notif("Messages","+1 (555) 019-2234","USPS: your package is on hold. Pay $1.99 now…","ok",'<div style="flex:none;width:30px;height:30px;border-radius:15px;background:#1F5FA8"></div>')+
 p("The real message is left alone; a clear warning sits on top.","#52606D",16),
 "Checked automatically from the message notification: link on the blocklists, lookalike name, pressure wording. Never says 'safe', only 'looks like a scam' or 'no known problems found'.", bg=shade_bg),
phone(4,"If she taps anyway",
 tag("CHROME")+'<div style="margin-top:50px;align-self:center;width:70px;height:70px;border-radius:35px;background:#D5DEE5"></div>'+
 h("This site can't be reached")+p("usps-redelivery.example","#52606D",17)+p("The website filter stopped the page from loading.","#3E4C59",17),
 "The filtering DNS blocks known bad sites even when she taps. Chrome shows its ordinary 'can't be reached' page, so we can't customise the wording."),
phone(5,"It's in her history",
 tag("HISTORY")+day("TODAY")+
 ev(A,"Flagged a scam text","You didn't open the link","11:15 am")+
 ev(G,"Blocked a dangerous website","usps-redelivery.example","11:16 am")+
 card(p("Messages like this are scams. You can delete it.","#1B2733",16))+nav("History"),
 "Both events appear in the history, so she or a guardian can see what happened."),
phone(6,"Guardians hear",
 tag("GUARDIAN'S PHONE · NTFY")+
 notif("ntfy · mom-7QK2","Scam text flagged","A text with a risky link arrived at 11:15 am. Mom didn't open it. The site was blocked.","ok",sun(30))+
 p("Short and calm. It contains no phone numbers, no message text and no location.","#52606D",16),
 "Sent automatically (she opted in at setup). Counts as 'handled', so no 24-hour follow-up.", bg=shade_bg),
]
board("ScamText.dc.html","A scam text is caught","SCENARIO 6 · AUTOMATIC LINK CHECKS","A “package on hold” scam text","Aegida checks links in message notifications by itself, and a one-time website filter backs it up. Mom doesn't have to share or scan anything.",st)
