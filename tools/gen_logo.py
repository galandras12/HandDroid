#!/usr/bin/env python3
"""Draws the HandDroid logo once and emits: an SVG, the Android vector drawables and the adaptive icon XML.

The artwork is an original drawing (a pineapple and a cocktail, as a nod to HandBrake) with the Android
robot head as a badge so the app is clearly the Android edition.
Usage: tools/gen_logo.py <res dir> <docs dir>
"""
import math, os, sys

BG = "#2C2F34"
SHAPES = []   # (d, fill, stroke, stroke_width)

def ellipse(cx, cy, rx, ry):
    return f"M{cx-rx:.2f},{cy:.2f}a{rx},{ry} 0 1,0 {2*rx},0a{rx},{ry} 0 1,0 {-2*rx},0Z"

def add(d, fill=None, stroke=None, sw=0):
    SHAPES.append((d, fill, stroke, sw))

# ---- cocktail glass (left) ----
add("M23,44H53Q53,60 38,64Q23,60 23,44Z", "#E3E8EC")                      # bowl
add("M25.5,49Q31,46.5 38,49T50.5,49Q49,58.5 38,61.5Q27,58.5 25.5,49Z", "#E5533D")  # drink
add("M36.6,63H39.4V76H36.6Z", "#C7CED5")                                   # stem
add(ellipse(38, 77.5, 9.5, 2.3), "#C7CED5")                                # base
add("M27,35Q38,24 49,35Z", "#43A047")                                      # umbrella
add("M38,29.5L36.2,52", None, "#F5F5F5", 1.2)                              # umbrella stick
add(ellipse(24.5, 43.5, 4.2, 4.2), "#F6C945")                              # lemon on the rim
add(ellipse(24.5, 43.5, 1.6, 1.6), "#FBE9A8")

# ---- pineapple (right) ----
CX, CY, RX, RY = 68, 63, 15.5, 20
add(ellipse(CX, CY, RX, RY), "#F2A91E")
def chord(c, sign):
    # lines x*sign + y = c through the body, clipped to a slightly smaller ellipse
    # solve intersection of line y = c - sign*x with ellipse (x-CX)^2/RX^2 + (y-CY)^2/RY^2 = 1
    k = 0.86
    a, b = RX * k, RY * k
    # param x = CX + u ; y = c - sign*(CX+u) -> v = y - CY = (c - sign*CX - CY) - sign*u
    m = c - sign * CX - CY
    A = 1 / a**2 + 1 / b**2
    B = -2 * sign * m / b**2
    C = m**2 / b**2 - 1
    disc = B * B - 4 * A * C
    if disc <= 0: return None
    u1 = (-B - math.sqrt(disc)) / (2 * A); u2 = (-B + math.sqrt(disc)) / (2 * A)
    return (CX + u1, c - sign * (CX + u1)), (CX + u2, c - sign * (CX + u2))
for sign in (1, -1):
    for i in range(-4, 5):
        # line passes through (CX, CY + i*7*sign_adjust)
        c = CY + i * 7 + sign * CX
        ch = chord(c, sign)
        if ch:
            (x1, y1), (x2, y2) = ch
            add(f"M{x1:.2f},{y1:.2f}L{x2:.2f},{y2:.2f}", None, "#B7770F", 1.1)
for i, (dx, dy) in enumerate([(-6, -7), (6, -7), (-6, 7), (6, 7), (0, 0)]):
    add(ellipse(CX + dx * 1.0, CY + dy * 1.0, 0.9, 0.9), "#8A5A0A")
# crown
add("M63,45L57,29L68,43Z", "#2E7D32")
add("M73,45L79,29L68,43Z", "#2E7D32")
add("M60,47L50,37L65,43Z", "#43A047")
add("M76,47L86,37L71,43Z", "#43A047")
add("M63,45L68,22L73,45Z", "#4CAF50")

# ---- android badge (bottom right) ----
BX, BY = 74.5, 76
add(ellipse(BX, BY, 12.5, 12.5), BG)
add(ellipse(BX, BY, 10.5, 10.5), "#3DDC84")
add(f"M{BX-6.8},{BY+2.4}A6.8,6.8 0 0 1 {BX+6.8},{BY+2.4}Z", "#1F2226")
add(ellipse(BX - 2.6, BY - 1.1, 1.0, 1.0), "#3DDC84")
add(ellipse(BX + 2.6, BY - 1.1, 1.0, 1.0), "#3DDC84")
add(f"M{BX-4.2},{BY-3.6}L{BX-5.6},{BY-5.9}", None, "#1F2226", 1.0)
add(f"M{BX+4.2},{BY-3.6}L{BX+5.6},{BY-5.9}", None, "#1F2226", 1.0)
add(f"M{BX-6.8},{BY+4.6}H{BX+6.8}", None, "#1F2226", 1.7)

# monochrome silhouette for themed icons / notification
MONO = [
    "M23,44H53Q53,60 38,64Q23,60 23,44Z", "M36.6,63H39.4V76H36.6Z", ellipse(38, 77.5, 9.5, 2.3),
    "M27,35Q38,24 49,35Z", ellipse(CX, CY, RX, RY), "M63,45L57,29L68,43Z", "M73,45L79,29L68,43Z",
    "M60,47L50,37L65,43Z", "M76,47L86,37L71,43Z", "M63,45L68,22L73,45Z",
]

def vec_path(d, fill, stroke, sw):
    a = [f'        <path android:pathData="{d}"']
    if fill: a.append(f'            android:fillColor="{fill}"')
    if stroke: a.append(f'            android:strokeColor="{stroke}" android:strokeWidth="{sw}" android:strokeLineCap="round"')
    return "\n".join(a) + "/>"

def vector(name, shapes, size_dp, bg=None, mono_fill=None):
    out = [f'<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           f'    android:width="{size_dp}dp" android:height="{size_dp}dp" android:viewportWidth="108" android:viewportHeight="108">']
    if bg: out.append(vec_path("M0,0H108V108H0Z", bg, None, 0).replace("        ", "    ", 1))
    for s in shapes:
        out.append(vec_path(*s))
    out.append("</vector>")
    return "\n".join(out) + "\n"

def svg():
    o = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">',
         f'<rect width="108" height="108" rx="24" fill="{BG}"/>']
    for d, f, s, w in SHAPES:
        o.append(f'<path d="{d}" fill="{f or "none"}"' + (f' stroke="{s}" stroke-width="{w}" stroke-linecap="round"' if s else "") + "/>")
    o.append("</svg>")
    return "\n".join(o) + "\n"

res, docs = sys.argv[1], sys.argv[2]
os.makedirs(f"{res}/drawable", exist_ok=True); os.makedirs(f"{res}/mipmap-anydpi-v26", exist_ok=True); os.makedirs(docs, exist_ok=True)
open(f"{res}/drawable/ic_launcher_foreground.xml", "w").write(vector("fg", SHAPES, 108))
open(f"{res}/drawable/logo_handdroid.xml", "w").write(vector("logo", SHAPES, 108, bg=BG))
mono = [(d, "#000000", None, 0) for d in MONO]
open(f"{res}/drawable/ic_launcher_monochrome.xml", "w").write(vector("mono", mono, 108))
open(f"{res}/drawable/ic_stat_handdroid.xml", "w").write(vector("stat", mono, 24))
adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/icon_bg" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''
for n in ("ic_launcher", "ic_launcher_round"):
    open(f"{res}/mipmap-anydpi-v26/{n}.xml", "w").write(adaptive)
open(f"{docs}/logo.svg", "w").write(svg())
print("logo written")
