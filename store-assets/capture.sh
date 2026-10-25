#!/usr/bin/env bash
# Takes the 6 raw store screenshots of Folio over adb into store-assets/raw/.
#
#   cd store-assets && ./capture.sh            # all six
#   ./capture.sh 02 05                         # only some of them
#
# Needs: one device connected (tuned on a 1080x2340 phone, SM-A165F), the dev build installed with
# storage access granted and the first-run flow finished, and the sample files from make_samples.py
# in samples/. Taps use resource ids where uiautomator can read the screen; the Files home keeps
# animating and cannot be dumped, so its taps are coordinates for 1080x2340.
set -u
cd "$(dirname "$0")"
export PKG=${PKG:-com.af010.creator.docreader}
export SHOTS=raw
. ./adb_ui.sh

DIR=/sdcard/Download/Folio
ONLY="$*"
want() { [ -z "$ONLY" ] || [[ " $ONLY " == *" $1 "* ]]; }

MIME_PDF=application/pdf
MIME_DOCX=application/vnd.openxmlformats-officedocument.wordprocessingml.document
MIME_XLSX=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
MIME_PPTX=application/vnd.openxmlformats-officedocument.presentationml.presentation

# --- device setup -------------------------------------------------------------------------------
demo_on() {
  adb shell settings put global sysui_demo_allowed 1
  local b="adb shell am broadcast -a com.android.systemui.demo"
  $b -e command enter >/dev/null
  $b -e command clock -e hhmm 0941 >/dev/null
  $b -e command battery -e level 100 -e plugged false >/dev/null
  $b -e command network -e wifi show -e level 4 >/dev/null
  $b -e command network -e mobile show -e datatype none -e level 4 >/dev/null
  $b -e command notifications -e visible false >/dev/null
  $b -e command status -e volume hide -e bluetooth hide -e alarm hide >/dev/null
}
demo_off() { adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null; }
trap demo_off EXIT

restart() { adb shell am force-stop "$PKG"; sleep 1; }

# Fresh copies every run, so edits from a previous run never show.
push_samples() {
  adb shell mkdir -p "$DIR"
  for f in samples/*; do
    adb push "$f" "$DIR/" >/dev/null 2>&1
    adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
      -d "file://$DIR/$(basename "$f" | sed 's/ /%20/g')" >/dev/null
  done
}

# An Office file left mid-edit by a previous run asks to restore the draft: take the saved file.
skip_draft() { has_text "Open saved file" && tap_text "Open saved file" 3; }
has_text() { ui_dump | grep -qF "text=\"$1\""; }

# Opens a file straight in its reader and makes sure that reader is on top (a cold start can lose it).
open_doc() {
  local path=$1 mime=$2 activity=$3
  for _ in 1 2 3; do
    restart
    open_file "$path" "$mime" 6
    adb shell dumpsys activity activities | grep -m1 topResumedActivity | grep -q "$activity" && return 0
    echo ".. $activity not on top, retrying"
  done
  echo "!! could not open $path" >&2; return 1
}

hide_keyboard() { adb shell input keyevent 111; sleep 0.8; }

# --- the six screens ------------------------------------------------------------------------------

# 01 Files home: every type in one place, Read / Create, new Word / Excel / PowerPoint.
shot_01_home() {
  restart
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 6
  tap_at 186 2200; sleep 2          # Files tab, in case another tab was kept
  shot 01_home
}

# 02 PDF signing: a typed signature placed on the tenant line, still selected.
shot_02_sign() {
  open_doc "$DIR/Rental Agreement.pdf" $MIME_PDF PdfReaderActivity || return
  if ui_dump | grep -q 'text="Page [^"]*Night"'; then tap_id btnNormal 3; fi
  tap_id btnSign 2
  tap_text "Type" 1
  tap_id edtSignature 1
  adb shell input text "Emma%sJohnson"; sleep 1
  hide_keyboard
  tap_text "Use this signature" 2
  adb shell input swipe 540 1128 775 1238 900; sleep 1.5   # onto the "Tenant signature" line
  shot 02_pdf_sign
}

# 03 Word editing: ribbon, a heading selected.
shot_03_word() {
  open_doc "$DIR/Project Proposal.docx" $MIME_DOCX OfficeActivity || return
  skip_draft
  tap_id btnEdit 3
  hide_keyboard
  adb shell input swipe 545 375 545 375 900; sleep 1.5      # long press "Green"
  hide_keyboard
  adb shell input swipe 633 440 845 440 800; sleep 1.5      # drag the end handle to "2027"
  shot 03_word_edit
}

# 04 Excel editing: formula bar and ribbon, a cell picked.
shot_04_excel() {
  open_doc "$DIR/Sales Report 2026.xlsx" $MIME_XLSX OfficeActivity || return
  skip_draft
  tap_id btnEdit 3
  hide_keyboard
  tap_at 735 1001; sleep 1.5                                # December profit
  hide_keyboard
  shot 04_excel_edit
}

# 05 Scanner: needs a real sheet of paper in front of the camera.
shot_05_scan() {
  restart
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 6
  tap_at 958 2200; sleep 2                                  # "+" -> What shall we make?
  tap_text "Scan a document" 4 540 1380
  tap_id btnManual 1
  echo
  echo ">> Put a printed page on a dark table, hold the phone above it until the blue outline"
  echo ">> sits on the paper edges, then press Enter here (keep holding the phone still)."
  read -r _
  shot 05a_scan_camera
  tap_id captureButton 4
  shot 05b_scan_crop
  tap_id btnNext 5                                          # "Looks good"
  shot 05_scan_filters
}

# 06 Create sheet: scan, photos to PDF, blank Word / Excel / PowerPoint.
shot_06_create() {
  restart
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 6
  tap_at 186 2200; sleep 1.5                                # Files tab behind the sheet
  tap_at 958 2200; sleep 2.5                                # "+"
  shot 06_create
}

# --- run ------------------------------------------------------------------------------------------
adb get-state >/dev/null || { echo "no device"; exit 1; }
mkdir -p raw
demo_on
push_samples
# Open the samples once so the recent list on the home screen shows them, not test files.
for f in "Meeting Notes.txt:text/plain:Activity" "Product Launch.pptx:$MIME_PPTX:OfficeActivity" \
         "Sales Report 2026.xlsx:$MIME_XLSX:OfficeActivity" "Project Proposal.docx:$MIME_DOCX:OfficeActivity" \
         "Rental Agreement.pdf:$MIME_PDF:PdfReaderActivity"; do
  IFS=: read -r name mime _ <<<"$f"
  want 01 && { restart; open_file "$DIR/$name" "$mime" 4; }
done

want 01 && shot_01_home
want 02 && shot_02_sign
want 03 && shot_03_word
want 04 && shot_04_excel
want 05 && shot_05_scan
want 06 && shot_06_create
restart
echo "done: $(ls raw | tr '\n' ' ')"
