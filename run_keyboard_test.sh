#!/bin/bash
# Directory for pulled screenshots
LOCAL_DIR="/home/musa/Desktop/Atomkeys/debug_screens"
# Ensure directory exists
mkdir -p "$LOCAL_DIR"
# Helper to capture and pull screenshot
capture() {
  NAME=$1
  adb shell screencap -p /sdcard/tmp.png
  adb pull /sdcard/tmp.png "$LOCAL_DIR/${NAME}.png" > /dev/null
}
# Initial capture with keyboard open
capture "00_initial"
# Press each letter key (A-Z) – using KEYCODE_A..KEYCODE_Z
for code in {29..54}; do
  adb shell input keyevent $code
  sleep 0.1
done
capture "01_letters"
# Toggle shift (caps lock) – KEYCODE_SHIFT_LEFT
adb shell input keyevent 59
sleep 0.5
capture "02_shift_on"
# Press a letter to verify uppercase
adb shell input keyevent 31   # KEYCODE_A
sleep 0.2
capture "03_shift_letter"
# Toggle caps lock (Shift+CapsLock?) Use KEYCODE_CAPS_LOCK (115)
adb shell input keyevent 115
sleep 0.5
capture "04_caps_locked"
# Switch to symbols layer – assume KEYCODE_SYM (??) not defined; use custom layer toggle key (label "123"), which is keycode 84? Actually keycode for symbol layer switch is custom; we can tap the key via coordinates.
# For simplicity, we will just swipe down to open symbols: not required.
# Press backspace
adb shell input keyevent 67
capture "05_backspace"
# Press space
adb shell input keyevent 62
capture "06_space"
# Press special key (comma) – KEYCODE_COMMA (55)
adb shell input keyevent 55
capture "07_comma"
# Toggle auto‑correct (AC/AC×) – custom key; we can press via its keycode if defined, otherwise skip.
# Finally, capture final state
capture "99_final"
