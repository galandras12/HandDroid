# Notice

**HandDroid** is based on **HandBrake** (https://github.com/HandBrake/HandBrake),
Copyright (c) 2003-2026 HandBrake Team, licensed under the GNU General Public License v2.

What was taken from HandBrake:

* the preset definitions (`preset/preset_builtin.json`), converted by `tools/convert_presets.py` into
  `app/src/main/assets/presets.json`;
* the structure and terminology of the settings (Summary / Video / Audio / Subtitles / Picture / Filters, RF quality,
  peak framerate, passthru, …).

HandDroid does not contain HandBrake's `libhb` engine or its graphics. The logo is an original drawing.

HandDroid itself is distributed under the **GNU General Public License v2** (see `LICENSE`).

## Bundled libraries

The app bundles `com.antonkarpenko:ffmpeg-kit-full-gpl` (FFmpeg 8.1.1 with x264, x265, libvpx, libaom, dav1d, Opus,
LAME, Vorbis, …), which is published under the GPL. The corresponding source code is available from the FFmpegKit
project (https://github.com/sk3llo/ffmpeg-kit-flutter) and the upstream projects of each library.
Jetpack Compose, Material 3, AndroidX, Kotlin and kotlinx are Apache-2.0 licensed.

### License compatibility note

The FFmpeg build inside `ffmpeg-kit-full-gpl` is configured with `--enable-gpl --enable-version3`, i.e. it is
GPL **v3**. GPL-2.0-only code and GPL-3.0 code cannot be combined into one distributed program. Before publishing
binaries, either license HandDroid's own code "GPL-2.0-or-later" (HandDroid's original code can then be combined with
GPLv3 components) or switch to an FFmpeg build without `--enable-version3`. This must be settled with the HandDroid
copyright holder; the HandBrake-derived preset data is GPL-2.0.

### Google ML Kit (automatic translation)

For languages the app does not ship, `com.google.mlkit:translate` translates the interface texts on the device. ML Kit is
proprietary software under the Google ML Kit / Google APIs terms, **not** open source, and is not covered by HandDroid's
GPL. Whether bundling it is compatible with the GPL must be checked before binaries are distributed; if not, remove the
`mlkit-translate` dependency and the `i18n/AutoTranslation.kt` hooks (HandDroid then falls back to English for unsupported languages).
