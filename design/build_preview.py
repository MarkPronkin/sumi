"""Bundle the design study without a web server or build dependencies."""
from pathlib import Path
import base64

root = Path(__file__).resolve().parent
fragment = (root / "options.fragment.html").read_text()
for token, filename in {
    "FRIEREN": "frieren.jpg",
    "FRIEREN_POSTER": "frieren-poster.jpg",
    "SOLO": "solo.jpg",
    "DANDADAN": "dandadan.jpg",
}.items():
    data = base64.b64encode((root / "assets" / filename).read_bytes()).decode()
    # Repeated assets become shared JS constants and DOM sources below.
    fragment = fragment.replace(f"__ASSET_{token}__", f"data:image/jpeg;base64,{data}")

# Deduplicate data URIs: each artwork is embedded exactly once, even if it appears
# in multiple layouts and in dynamically rendered search/detail screens.
import re
images = list(dict.fromkeys(re.findall(r"data:image/jpeg;base64,[A-Za-z0-9+/=]+", fragment)))
for index, uri in enumerate(images):
    fragment = fragment.replace('src="' + uri + '"', f'data-art="{index}"')
    fragment = fragment.replace("'" + uri + "'", f"window.__crArt[{index}]")
image_bootstrap = '<script>window.__crArt=' + __import__('json').dumps(images) + ';</script>'
asset_init = '<script>document.querySelectorAll("[data-art]").forEach(el=>el.src=window.__crArt[Number(el.dataset.art)]);</script>'
fragment = image_bootstrap + fragment + asset_init

document = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Crunchyroll Android — OLED design directions</title>
<style>:root{color-scheme:dark;--background:#19191c;--foreground:#efeff1;--secondary:#29292e;--border:#3b3b41;--muted-foreground:#aaaab3;--radius:12px}html,body{margin:0;padding:0;background:transparent}body{font-family:Arial,sans-serif}button,input{font-family:inherit}</style>
</head><body>""" + fragment + "</body></html>"
output = root / "crunchyroll-oled-design-options.html"
output.write_text(document)
assert len(document.encode()) < 1_000_000, "Preview must stay under 1 MB"
print(f"{output}: {len(document.encode()):,} bytes")
