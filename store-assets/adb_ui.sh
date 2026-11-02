# Helpers to drive Folio over adb by resource id / text. Source it: . ./adb_ui.sh
PKG=${PKG:-com.af010.creator.docreader}
SHOTS=${SHOTS:-raw}

# Screens that keep animating (Files home) never go idle and cannot be dumped: callers fall back to coordinates.
ui_dump() { adb shell rm -f /sdcard/ui.xml; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb exec-out cat /sdcard/ui.xml; }

# Centre of the first node matching an attribute: center 'resource-id="pkg:id/btnSign"'
center() {
  ui_dump | tr '>' '\n' | grep -F "$1" | head -1 \
    | sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p' \
    | awk '{ printf "%d %d", ($1+$3)/2, ($2+$4)/2 }'
}

tap_at() { adb shell input tap "$1" "$2"; }
# tap_id NAME [sleep] [x y]: the optional x y are used when the node cannot be found (1080x2340 screen).
_tap() {
  local c; c=$(center "$1")
  if [ -z "$c" ] && [ -n "${4:-}" ]; then c="$3 $4"; fi
  [ -n "$c" ] || { echo "!! $1 not found" >&2; return 1; }
  tap_at $c; sleep "${2:-1.2}"
}
tap_id()   { _tap "resource-id=\"$PKG:id/$1\"" "${2:-1.2}" "${3:-}" "${4:-}"; }
tap_text() { _tap "text=\"$1\"" "${2:-1.2}" "${3:-}" "${4:-}"; }
tap_desc() { _tap "content-desc=\"$1\"" "${2:-1.2}" "${3:-}" "${4:-}"; }
has_id()   { ui_dump | grep -qF "resource-id=\"$PKG:id/$1\""; }

# Path through points with a finger kept down: draw x1 y1 x2 y2 ...
draw() {
  adb shell input motionevent DOWN "$1" "$2"; shift 2
  while [ $# -ge 2 ]; do adb shell input motionevent MOVE "$1" "$2"; shift 2; done
  adb shell input motionevent UP 0 0 >/dev/null 2>&1 || true
}

open_file() { # open_file "/sdcard/Download/Folio/x.pdf" mime
  local uri="file://$(echo "$1" | sed 's/ /%20/g')"
  adb shell am start -a android.intent.action.VIEW -d "$uri" -t "$2" -n "$PKG/.ui.splash.SplashActivity" >/dev/null
  sleep "${3:-5}"
}

shot() { mkdir -p "$SHOTS"; adb exec-out screencap -p > "$SHOTS/$1.png"; echo "saved $SHOTS/$1.png"; }
