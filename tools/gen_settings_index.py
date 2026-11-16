"""Rigenera SettingsIndex.kt leggendo le voci di SettingsScreen.kt (uso: gen_index.py <SettingsScreen.kt> [--print])."""
import re, sys
src = open(sys.argv[1], encoding="utf-8").read()
route = dict(re.findall(r'"(\w+)"(?:, "\w+")? -> (Page\w+)\(', src))
fn_page = {}
for k, f in route.items():
    fn_page.setdefault(f, k)
fn_page["PageTheme"] = "theme"
fn_page["SensationSettings"] = "theme"
fn_page["MatEditor"] = "material"
fn_page["PageInfo"] = "info"
STR = r'"((?:[^"\\]|\\.)*)"'
pat = re.compile(
    r'(?P<fun>^private fun (?P<fname>\w+)\()'
    r'|(?P<grp>\b(?:Group|OrientedGroup|SectionTitle)\(\s*' + STR.replace('(', '(?P<g>', 1) + ')'
    r'|(?P<row>\b(?:SwitchRow|SliderRow|ChoiceRow)\(\s*' + STR.replace('(', '(?P<t>', 1) + r'(?:\s*,\s*(?:' + STR.replace('(', '(?P<h>', 1) + r'|null))?)'
    r'|(?P<link>\bStyleLink\(\s*vm\s*,\s*' + STR.replace('(', '(?P<lt>', 1) + ')'
    r'|(?P<li>headlineContent\s*=\s*\{\s*Text\(\s*' + STR.replace('(', '(?P<it>', 1) + r'\s*[,)]'+ r')'
    , re.M)
out, seen = [], set()
page = None; group = ""; cur = ""
for m in pat.finditer(src):
    if m.group("fun"):
        cur = m.group("fname"); page = fn_page.get(cur); group = "Tema Sensation" if m.group("fname") == "SensationSettings" else ""; continue
    if page is None: continue
    if m.group("grp"):
        group = m.group("g"); continue
    if m.group("row"):
        title, hint = m.group("t"), m.group("h") or ""
    elif m.group("link"):
        title, hint = m.group("lt"), ""
    else:
        title, hint = m.group("it"), ""
    if "$" in title or not title.strip(): continue
    if "$" in hint: hint = ""
    key = (title, page)
    if key in seen: continue
    seen.add(key)
    if page == "theme" and group == "Scegli il tema": group = "Impostazioni del tema"
    if cur == "MatEditor" and not hint: hint = "Dentro ogni elemento: dock, cartelle, widget, menu\u2026"
    out.append((title, page, group, hint))
if "--print" in sys.argv:
    for o in out: print(" | ".join(o))
else:
    body = ",\n".join('    SettingEntry("%s", "%s", "%s", "%s")' % o for o in out)
    print("package com.marco.mlauncher\n\n// File generato dalle voci di SettingsScreen.kt (tools/gen_settings_index.py): serve alla ricerca nelle impostazioni.\n\nclass SettingEntry(val title: String, val page: String, val group: String, val hint: String)\n\nval SETTINGS_INDEX = listOf(\n" + body + "\n)")
