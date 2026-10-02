#!/usr/bin/env python3
"""Generates res/values/strings.xml (English) and res/values-hu/strings.xml (Hungarian) from one table."""
import os, sys
from xml.sax.saxutils import escape

S = {
 "app_name": ("HandDroid", "HandDroid"),
 "nav_convert": ("Convert", "Konvertálás"),
 "nav_presets": ("Presets", "Presetek"),
 "nav_queue": ("Queue", "Sor"),
 "nav_settings": ("Settings", "Beállítások"),
 "back": ("Back", "Vissza"),
 "cancel": ("Cancel", "Mégse"),
 "save": ("Save", "Mentés"),
 "delete": ("Delete", "Törlés"),
 "remove": ("Remove", "Eltávolítás"),
 "open": ("Open", "Megnyitás"),
 "share": ("Share", "Megosztás"),
 "retry": ("Retry", "Újra"),
 "off": ("Off", "Ki"),
 "none": ("None", "Nincs"),
 "auto": ("Auto", "Automatikus"),
 "working": ("Working…", "Folyamatban…"),
 # source
 "open_source": ("Open source", "Forrás megnyitása"),
 "open_source_hint": ("Pick one or more videos from your device. Almost any codec or container works.", "Válassz egy vagy több videót az eszközről. Szinte bármilyen kodek és konténer megfelel."),
 "change_source": ("Change source", "Forrás cseréje"),
 "scanning": ("Scanning source…", "Forrás vizsgálata…"),
 "tracks_found": ("%1$d audio · %2$d subtitle tracks", "%1$d hangsáv · %2$d feliratsáv"),
 "msg_probe_failed": ("Couldn't read %1$s", "Nem olvasható: %1$s"),
 "msg_added_to_queue": ("Added %1$d to the queue", "%1$d elem a sorhoz adva"),
 "msg_preset_saved": ("Preset “%1$s” saved", "„%1$s” preset mentve"),
 # presets
 "preset": ("Preset", "Preset"),
 "modified": ("modified", "módosítva"),
 "change_preset": ("Presets", "Presetek"),
 "save_preset": ("Save as preset", "Mentés presetként"),
 "preset_name": ("Preset name", "Preset neve"),
 "search_presets": ("Search presets", "Preset keresése"),
 "cat_custom": ("My presets", "Saját presetek"),
 "cat_general": ("General", "Általános"),
 "cat_web": ("Web", "Web"),
 "cat_devices": ("Devices", "Eszközök"),
 "cat_matroska": ("Matroska", "Matroska"),
 "cat_professional": ("Professional", "Professzionális"),
 "cat_hardware": ("Hardware (Android)", "Hardveres (Android)"),
 # tabs
 "tab_summary": ("Summary", "Összegzés"),
 "tab_video": ("Video", "Videó"),
 "tab_audio": ("Audio", "Hang"),
 "tab_subtitles": ("Subtitles", "Feliratok"),
 "tab_picture": ("Picture", "Kép"),
 "tab_filters": ("Filters", "Szűrők"),
 # summary
 "output": ("Output", "Kimenet"),
 "format": ("Container", "Konténer"),
 "web_optimized": ("Web optimized", "Webre optimalizált"),
 "web_optimized_hint": ("Moves the index to the start so playback can begin while streaming (MP4 only).", "Az indexet az elejére teszi, így streamelés közben azonnal indulhat a lejátszás (csak MP4)."),
 "chapter_markers": ("Chapter markers", "Fejezetjelölők"),
 "file_name": ("File name", "Fájlnév"),
 "batch_names_hint": ("Each file keeps its original name.", "Minden fájl megtartja az eredeti nevét."),
 "saved_to_default": ("Saved to Movies/HandDroid", "Mentés ide: Movies/HandDroid"),
 "saved_to_custom": ("Saved to the output folder chosen in Settings", "Mentés a Beállításokban választott kimeneti mappába"),
 "summary_title": ("Settings summary", "Beállítások összegzése"),
 "res_original": ("Original size", "Eredeti méret"),
 "track_none": ("None", "Nincs"),
 "track_first": ("First track", "Első sáv"),
 "track_all": ("All tracks", "Minden sáv"),
 # video
 "video_encoder": ("Video codec", "Videókodek"),
 "hw_hint": ("Uses the phone's hardware encoder: very fast, bitrate mode only, results depend on the device. Falls back to software encoding if it fails.", "A telefon hardveres kódolóját használja: nagyon gyors, csak átlagos bitráta módban, az eredmény eszközfüggő. Hiba esetén automatikusan szoftveres kódolásra vált."),
 "av1_hint": ("AV1 is very slow on phones. Use a fast preset and expect long encodes.", "Az AV1 telefonon nagyon lassú. Használj gyors presetet, és hosszú kódolási időre számíts."),
 "framerate": ("Framerate (FPS)", "Képkockasebesség (FPS)"),
 "same_as_source": ("Same as source", "Mint a forrás"),
 "fps_peak": ("Peak", "Maximum"),
 "fps_constant": ("Constant", "Állandó"),
 "fps_variable": ("Variable", "Változó"),
 "quality": ("Quality", "Minőség"),
 "quality_constant": ("Constant quality", "Állandó minőség"),
 "quality_bitrate": ("Avg bitrate", "Átlag bitráta"),
 "constant_quality": ("Constant quality", "Állandó minőség"),
 "lower_quality": ("Lower quality", "Alacsonyabb minőség"),
 "higher_quality": ("Higher quality", "Magasabb minőség"),
 "avg_bitrate": ("Average bitrate", "Átlagos bitráta"),
 "two_pass": ("2-pass encoding", "2 menetes kódolás"),
 "optimise_video": ("Optimise video", "Videó optimalizálása"),
 "encoder_preset": ("Encoder preset", "Kódoló preset"),
 "faster": ("Faster", "Gyorsabb"),
 "slower": ("Slower, smaller", "Lassabb, kisebb"),
 "profile": ("Profile", "Profil"),
 "level": ("Level", "Szint"),
 "tune": ("Tune", "Hangolás"),
 "extra_options": ("Extra options", "További opciók"),
 # audio
 "audio_tracks": ("Tracks to convert", "Átalakítandó sávok"),
 "no_audio_in_source": ("The source has no audio.", "A forrásban nincs hang."),
 "encoder_settings": ("Encoder settings", "Kódoló beállítások"),
 "audio_encoder": ("Audio codec", "Hangkodek"),
 "auto_passthru": ("Auto passthru (copy)", "Automatikus átmásolás"),
 "auto_passthru_hint": ("Copies the original audio when the container supports it, otherwise converts to AAC (Opus for WebM).", "Az eredeti hangot másolja, ha a konténer támogatja, különben AAC-ra (WebM esetén Opusra) alakítja."),
 "passthru_matching": ("Copy matching tracks", "Egyező sávok másolása"),
 "passthru_matching_hint": ("If the source already uses this codec, copy it instead of re-encoding.", "Ha a forrás már ezt a kodeket használja, újrakódolás helyett másolja."),
 "mixdown": ("Mixdown", "Csatornák"),
 "mix_auto": ("Keep source", "Mint a forrás"),
 "mix_mono": ("Mono", "Mono"),
 "mix_stereo": ("Stereo", "Sztereó"),
 "mix_51": ("5.1 surround", "5.1 térhatású"),
 "bitrate": ("Bitrate", "Bitráta"),
 "samplerate": ("Sample rate", "Mintavételi frekvencia"),
 "gain": ("Gain", "Hangerő"),
 # subtitles
 "subtitles_hint": ("Soft subtitles are kept as selectable tracks. Burning subtitles into the picture is not supported yet.", "A felirat kapcsolható sávként marad meg. A kép közé égetés még nem támogatott."),
 "subtitles_webm": ("WebM files can't carry these subtitles.", "A WebM fájl nem tartalmazhat ilyen feliratot."),
 "no_subs_in_source": ("The source has no subtitles.", "A forrásban nincs felirat."),
 "not_supported_mp4": ("picture based, skipped in MP4", "képalapú, MP4-ben kihagyva"),
 # picture
 "size": ("Size", "Méret"),
 "max_resolution": ("Maximum resolution", "Legnagyobb felbontás"),
 "res_hint": ("The picture is scaled down to fit, never enlarged. Aspect ratio is kept.", "A kép arányosan kisebbre skálázódik, nagyítás nincs. A képarány megmarad."),
 "rotation": ("Rotation", "Forgatás"),
 "rot_none": ("None", "Nincs"),
 "rot_cw90": ("90° clockwise", "90° jobbra"),
 "rot_180": ("180°", "180°"),
 "rot_ccw90": ("90° counter-clockwise", "90° balra"),
 "rot_hflip": ("Flip horizontally", "Vízszintes tükrözés"),
 "crop": ("Crop", "Vágás"),
 "crop_top": ("Top", "Felül"),
 "crop_bottom": ("Bottom", "Alul"),
 "crop_left": ("Left", "Balra"),
 "crop_right": ("Right", "Jobbra"),
 # filters
 "deinterlace": ("Deinterlace", "Sorfésűlés-mentesítés"),
 "deint_decomb": ("Decomb (only interlaced frames)", "Decomb (csak az összefésült képkockák)"),
 "deint_yadif": ("Yadif", "Yadif"),
 "deint_bwdif": ("Bwdif (higher quality)", "Bwdif (jobb minőség)"),
 "denoise": ("Denoise", "Zajszűrés"),
 "sharpen": ("Sharpen", "Élesítés"),
 "strength_light": ("Light", "Gyenge"),
 "strength_medium": ("Medium", "Közepes"),
 "strength_strong": ("Strong", "Erős"),
 "grayscale": ("Grayscale", "Szürkeárnyalat"),
 # actions
 "add_to_queue": ("Add to queue", "Sorhoz adás"),
 "start_encode": ("Start", "Indítás"),
 # queue
 "start_queue": ("Start queue", "Sor indítása"),
 "stop_queue": ("Stop", "Leállítás"),
 "clear_finished": ("Clear finished", "Kész elemek törlése"),
 "queue_empty": ("The queue is empty", "A sor üres"),
 "queue_empty_hint": ("Open a video on the Convert tab and add it to the queue.", "Nyiss meg egy videót a Konvertálás fülön, és add hozzá a sorhoz."),
 "status_waiting": ("Waiting", "Várakozik"),
 "status_done": ("Done", "Kész"),
 "status_failed": ("Failed", "Sikertelen"),
 "status_cancelled": ("Cancelled", "Megszakítva"),
 "hw_fallback": ("Hardware encoder failed, using software encoding.", "A hardveres kódoló hibázott, szoftveres kódolást használ."),
 "eta_short": ("%1$s left", "még %1$s"),
 "activity_log": ("Activity log", "Tevékenységnapló"),
 "log_empty": ("Nothing logged yet.", "Még nincs bejegyzés."),
 # notifications
 "channel_progress": ("Encoding progress", "Kódolás állapota"),
 "channel_done": ("Finished encodes", "Befejezett kódolások"),
 "action_stop": ("Stop", "Leállítás"),
 "notif_encoding": ("Encoding %1$d/%2$d: %3$s", "Kódolás %1$d/%2$d: %3$s"),
 "notif_finished_title": ("HandDroid", "HandDroid"),
 "notif_finished_mixed": ("%1$d finished, %2$d failed", "%1$d kész, %2$d sikertelen"),
 # settings
 "appearance": ("Appearance", "Megjelenés"),
 "theme_system": ("System", "Rendszer"),
 "theme_light": ("Light", "Világos"),
 "theme_dark": ("Dark", "Sötét"),
 "output_folder": ("Output folder", "Kimeneti mappa"),
 "default_folder": ("Movies/HandDroid", "Movies/HandDroid"),
 "output_folder_hint": ("Converted videos are saved here.", "Az elkészült videók ide kerülnek."),
 "choose_folder": ("Choose folder", "Mappa választása"),
 "use_default": ("Use default", "Alapértelmezett"),
 # about
 "about_handdroid": ("About HandDroid", "A HandDroid névjegye"),
 "about_summary": ("Version, license and credits", "Verzió, licenc és köszönetnyilvánítás"),
 "version_fmt": ("Version %1$s", "%1$s verzió"),
 "based_on_handbrake": ("Based on HandBrake", "A HandBrake alapján"),
 "about_body": ("HandDroid is an Android video converter inspired by HandBrake. It turns videos from almost any codec into compact MP4, MKV or WebM files, using HandBrake's presets or detailed settings of your own. The presets and the structure of the settings come from the HandBrake project; the conversion itself is done by FFmpeg with x264, x265, libvpx and libaom.", "A HandDroid a HandBrake alapján készült Android videókonverter. Szinte bármilyen kodekű videóból kis méretű MP4, MKV vagy WebM fájlt készít, a HandBrake presetjeivel vagy saját, részletes beállításokkal. A presetek és a beállítások felépítése a HandBrake projektből származik; magát az átalakítást az FFmpeg végzi x264, x265, libvpx és libaom kodekekkel."),
 "about_disclaimer": ("HandDroid is an independent project. It is not affiliated with or endorsed by the HandBrake Team. HandBrake is Copyright © 2003–2026 HandBrake Team.", "A HandDroid független projekt, nem áll kapcsolatban a HandBrake csapattal, és nem általuk támogatott. A HandBrake © 2003–2026 HandBrake Team."),
 "license": ("License", "Licenc"),
 "license_body": ("HandDroid is free software, released under the GNU General Public License version 2 (GPL-2.0), the same license as HandBrake. You can use, study, share and modify it under the terms of that license.", "A HandDroid szabad szoftver, a GNU General Public License 2. verziója (GPL-2.0) alatt jelenik meg, ugyanúgy mint a HandBrake. A licenc feltételei szerint használhatod, tanulmányozhatod, megoszthatod és módosíthatod."),
 "view_license": ("View license text", "Licenc szövege"),
 "links": ("Links", "Hivatkozások"),
 "source_code": ("HandDroid source", "HandDroid forráskód"),
 "handbrake_source": ("HandBrake source code", "HandBrake forráskód"),
 "third_party": ("Third party components", "Külső komponensek"),
 "third_party_body": ("FFmpeg (GPL) via FFmpegKit full-gpl · x264 · x265 · libvpx · libaom · dav1d · Opus · LAME · Vorbis · libass · Jetpack Compose and Material 3 (Apache-2.0). All of them keep their own licenses and copyrights.", "FFmpeg (GPL) az FFmpegKit full-gpl csomagon át · x264 · x265 · libvpx · libaom · dav1d · Opus · LAME · Vorbis · libass · Jetpack Compose és Material 3 (Apache-2.0). Mindegyik megtartja saját licencét és szerzői jogát."),
}

PLURALS = {
 "more_files": (("one", "+%1$d more file", "+%1$d további fájl"), ("other", "+%1$d more files", "+%1$d további fájl")),
 "notif_finished_ok": (("one", "%1$d video converted", "%1$d videó átalakítva"), ("other", "%1$d videos converted", "%1$d videó átalakítva")),
}

def esc(s):
    s = escape(s).replace("'", "\\'").replace('"', '\\"')
    if s.startswith("@") or s.startswith("?"): s = "\\" + s
    return s

def write(path, idx):
    out = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for k, v in S.items():
        out.append(f'    <string name="{k}">{esc(v[idx])}</string>')
    for k, items in PLURALS.items():
        out.append(f'    <plurals name="{k}">')
        for q, en, hu in items:
            out.append(f'        <item quantity="{q}">{esc((en, hu)[idx])}</item>')
        out.append('    </plurals>')
    out.append('</resources>')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8").write("\n".join(out) + "\n")

res = sys.argv[1]
write(f"{res}/values/strings.xml", 0)
write(f"{res}/values-hu/strings.xml", 1)
print(len(S), "strings")
