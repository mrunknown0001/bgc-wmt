#!/usr/bin/env bash
# Renders the Play Store graphics from their SVG sources with headless Chrome.
# Run from anywhere:  bash play-store/graphics/render.sh
#
#   icon.svg            -> icon-512.png           (Play Console: App icon, 512x512 PNG)
#   feature-graphic.svg -> feature-graphic.jpg    (Play Console: Feature graphic, 1024x500,
#                                                   JPEG because Play refuses a PNG with alpha)
#
# The Play icon is cropped to the inner 90 of the 108-unit adaptive grid so the
# mark sits at roughly the size a launcher shows it; Play adds its own rounding.
set -euo pipefail
cd "$(dirname "$0")"

CHROME="${CHROME:-}"
for c in "/c/Program Files/Google/Chrome/Application/chrome.exe" \
         "/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" \
         "$(command -v google-chrome || true)" "$(command -v chromium || true)"; do
  [[ -z "$CHROME" && -n "$c" && -x "$c" ]] && CHROME="$c"
done
[[ -n "$CHROME" ]] || { echo "No Chrome/Edge found; set CHROME=/path/to/chrome" >&2; exit 1; }

winpath() { command -v cygpath >/dev/null && cygpath -w "$1" || echo "$1"; }
fileurl() { echo "file:///$(command -v cygpath >/dev/null && cygpath -m "$PWD/$1" || echo "${PWD#/}/$1")"; }

shoot() { # <svg> <out> <w> <h>
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
    --default-background-color=00000000 --window-size="$3,$4" \
    --screenshot="$(winpath "$PWD/$2")" "$(fileurl "$1")" 2>/dev/null
  echo "wrote $2"
}

sed 's/viewBox="0 0 108 108"/viewBox="9 9 90 90"/' icon.svg > .icon-play.svg
shoot .icon-play.svg icon-512.png 512 512
rm -f .icon-play.svg
shoot feature-graphic.svg feature-graphic.jpg 1024 500
