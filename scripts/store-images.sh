#!/bin/sh
# store-images.sh frames the scenes StoreScreenshotTest renders (make store-images) with a
# caption on the brand background, and writes them, the icon and the feature graphic into the
# fastlane listing that F-Droid reads.
#
# Needs ImageMagick 7 with Pango, and the Noto Serif and Noto Sans fonts.
set -eu

scenes=app/build/screenshots
images=fastlane/metadata/android/en-US/images
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

command -v magick >/dev/null || { echo "ImageMagick 7 (magick) is required" >&2; exit 1; }
magick -list format | grep -q PANGO || { echo "ImageMagick needs Pango support" >&2; exit 1; }
for family in "Noto Serif" "Noto Sans"; do
    fc-list | grep -q "$family" || { echo "The $family font is required" >&2; exit 1; }
done
[ -f "$scenes/store_1_bubble.png" ] || { echo "Render the scenes first: make store-images" >&2; exit 1; }

pine="#00382E"
cream="#F3F0E6"
mint="#86D5C2"
width=1080
height=2340
shot_width=840
shot_top=470

# caption stacks a serif title over a sans subtitle, so a title that wraps pushes the
# subtitle down instead of running into it.
caption() {
    # sh has no local variables, so these names must not clash with frame's.
    caption_box=$3 caption_align=$6
    magick -background none -define "pango:align=$caption_align" -size "${caption_box}x" \
        "pango:<span font='Noto Serif SemiBold $4' foreground='$cream'>$1</span>" "$work/title.png"
    magick -background none -define "pango:align=$caption_align" -size "${caption_box}x" \
        "pango:<span font='Noto Sans $5' foreground='$mint'>$2</span>" "$work/subtitle.png"
    magick "$work/title.png" -size "${caption_box}x22" xc:none "$work/subtitle.png" -background none -append "$7"
}

# frame writes one listing screenshot: caption on top, the scene with rounded corners below.
# An optional crop offset picks a phone-sized window out of a tall scene.
frame() {
    scene=$1 output=$2 title=$3 subtitle=$4 offset=${5:-}
    source="$scenes/$scene.png"
    if [ -n "$offset" ]; then
        magick "$source" -crop "1233x2673+0+$offset" +repage "$work/cropped.png"
        source="$work/cropped.png"
    fi
    magick "$source" -resize "${shot_width}x" "$work/shot.png"
    shot_height=$(magick identify -format '%h' "$work/shot.png")
    magick "$work/shot.png" \
        \( +clone -alpha extract -fill black -colorize 100 -fill white \
           -draw "roundrectangle 0,0,$((shot_width - 1)),$((shot_height - 1)),44,44" \) \
        -alpha off -compose CopyOpacity -composite "$work/rounded.png"
    magick "$work/rounded.png" \( +clone -background black -shadow 45x28+0+18 \) +swap \
        -background none -layers merge +repage "$work/shadowed.png"
    caption "$title" "$subtitle" 960 62 34 center "$work/caption.png"
    magick -size "${width}x${height}" "xc:$pine" \
        "$work/caption.png" -gravity north -geometry +0+120 -composite \
        "$work/shadowed.png" -gravity north -geometry "+0+$((shot_top - 40))" -composite \
        -strip "$images/phoneScreenshots/$output.png"
}

mkdir -p "$images/phoneScreenshots"
rm -f "$images"/phoneScreenshots/*.png

frame store_1_bubble 1 "Dictate into any app" "Tap the bubble that waits by your keyboard"
frame store_2_keyboard 2 "Or use your keyboard" "Its mic key opens Sasayaki. Works with HeliBoard, FlorisBoard and more"
frame store_3_home 3 "Speak, don't type" "See how much time dictation saves you"
frame store_4_style 4 "Clean text, your way" "Optional cleanup with a chat model, styled per profile" 2860
frame store_5_provider 5 "Your own servers" "Any OpenAI-compatible server you trust"
frame store_6_history 6 "Kept on your phone" "Search, copy or retry past dictations. No tracking, no cloud."

# The scene renders the round logo on a white window; the circle mask keeps only the logo.
magick "$scenes/store_icon.png" -alpha set \
    \( +clone -alpha extract -fill black -colorize 100 -fill white -draw "circle 255.5,255.5 255.5,0" \) \
    -alpha off -compose CopyOpacity -composite -strip "$images/icon.png"

magick "$images/icon.png" -resize 280x280 "$work/logo.png"
caption "Sasayaki" "Dictation for any app, on speech servers you choose" 520 80 28 left "$work/brand.png"
magick -size 1024x500 "xc:$pine" \
    "$work/logo.png" -gravity west -geometry +90+0 -composite \
    "$work/brand.png" -gravity west -geometry +430+0 -composite \
    -strip "$images/featureGraphic.png"

echo "Wrote $(ls "$images/phoneScreenshots" | wc -l) screenshots, icon.png and featureGraphic.png to $images"
