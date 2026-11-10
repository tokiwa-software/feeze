#!/usr/bin/env bash

# This file is part of the Feeze scheduling analysis tool.
#
# This code is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License, version 3,
# as published by the Free Software Foundation.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License, version 3,
# along with this program.  If not, see <http://www.gnu.org/licenses/>

# -----------------------------------------------------------------------
#
# Copyright (c) 2026, Tokiwa Software GmbH, Germany
#
# Render one application icon PNG of a given size from an SVG master image.
#
#   usage: generate_icon.sh <input.svg> <output.png> <size>
#
# The rendering deliberately avoids anti-aliasing and snaps the colours to
# the colours present in the SVG master to keep small icons crisp.
#
# Requires inkscape and ImageMagick ('magick' from IM7, falling back to
# 'convert' from IM6, e.g. on Ubuntu 24.04).
#
# -----------------------------------------------------------------------

set -e

svg=$1
out=$2
s=$3
M=1536
f=$((M/s))

# ImageMagick 7 has the 'magick' command, IM6 (e.g. Ubuntu 24.04) only 'convert'
if command -v magick >/dev/null; then
  MAGICK=magick
elif command -v convert >/dev/null; then
  MAGICK=convert
else
  echo "*** error: neither 'magick' nor 'convert' (ImageMagick) found, please install ImageMagick" >&2
  exit 1
fi

# private temporary directory, removed on exit (also safe for parallel make)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

master=$tmp/master.png
palette=$tmp/palette.png
rgb=$tmp/rgb.png
mask=$tmp/mask.png

# 1. Master render (transparent background kept)
inkscape "$svg" --export-type=png --export-filename="$master" \
  -w $M -h $M

# 2. Palette from the opaque colours only (no transparent entry)
args=()
for c in $("$MAGICK" "$master" -unique-colors txt:- | grep -oE '#[0-9A-Fa-f]{6}FF' | sed 's/FF$//'); do
  args+=(xc:"$c")
done
if [ ${#args[@]} -eq 0 ]; then
  echo "*** error: no opaque colours found in $svg" >&2
  exit 1
fi
"$MAGICK" "${args[@]}" +append "$palette"

# 3a. Colour: flatten onto black, brightest per block, snap to palette
"$MAGICK" "$master" -background black -alpha remove -alpha off \
  -statistic Maximum ${f}x${f} -filter point -resize ${s}x${s}! \
  +dither -remap "$palette" "$rgb"

# 3b. Alpha: binary mask (opaque if any pixel in the block is opaque)
"$MAGICK" "$master" -alpha extract \
  -statistic Maximum ${f}x${f} -filter point -resize ${s}x${s}! \
  -threshold 50% "$mask"

# 3c. Combine
"$MAGICK" "$rgb" "$mask" -alpha off -compose CopyOpacity -composite PNG32:"$out"
