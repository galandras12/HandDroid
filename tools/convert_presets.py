#!/usr/bin/env python3
"""Convert HandBrake's preset_builtin.json into HandDroid's app/src/main/assets/presets.json.

Usage: tools/convert_presets.py <HandBrake>/preset/preset_builtin.json app/src/main/assets/presets.json

Only presets that HandDroid can run with the FFmpeg build bundled in the app are kept
(x264, x265, VP9, AV1 in MP4/MKV). Desktop-only hardware encoders and pro formats are skipped.
Android specific hardware (MediaCodec) presets are appended at the end.
"""
import json, sys

ENC = {"x264": "X264", "x265_10bit": "X265_10BIT", "vp9": "VP9", "svt_av1_10bit": "AV1"}
X26X_SPEEDS = ["placebo", "veryslow", "slower", "slow", "medium", "fast", "faster", "veryfast", "superfast", "ultrafast"]
FILTER_DEINT = {"off": "OFF", "decomb": "DECOMB", "yadif": "YADIF"}


def speed_of(encoder, preset):
    if encoder in ("x264", "x265_10bit"):
        return X26X_SPEEDS.index(preset) if preset in X26X_SPEEDS else 4
    if encoder == "svt_av1_10bit":  # SVT-AV1 preset 0 (slow) .. 13 (fast)
        return max(0, min(9, round(int(preset) * 9 / 13)))
    if encoder == "vp9":
        return 4
    return 4


def convert(p, cat):
    enc = p["VideoEncoder"]
    if enc not in ENC or p["FileFormat"] not in ("mp4", "mkv"):
        return None
    audio = p["AudioList"][0] if p["AudioList"] else None
    a_enc = (audio or {}).get("AudioEncoder", "aac")
    pass_mask = a_enc.startswith("copy")
    audio_encoder = "COPY" if pass_mask else {"aac": "AAC", "opus": "OPUS"}.get(a_enc, "AAC")
    mix = {"stereo": "STEREO", "mono": "MONO", "5point1": "SURROUND_5_1"}.get((audio or {}).get("AudioMixdown"), "AUTO")
    bitrate = int((audio or {}).get("AudioBitrate", 160))
    if bitrate <= 0:
        bitrate = 160
    cq = p["VideoQualityType"] == 2
    x26x = enc in ("x264", "x265_10bit")
    s = {
        "container": p["FileFormat"].upper(),
        "webOptimized": bool(p.get("Optimize")),
        "chapterMarkers": bool(p.get("ChapterMarkers", True)),
        "videoEncoder": ENC[enc],
        "framerate": 0 if p["VideoFramerate"] == "auto" else int(p["VideoFramerate"]),
        "framerateMode": {"pfr": "PEAK", "cfr": "CONSTANT", "vfr": "VARIABLE"}[p["VideoFramerateMode"]],
        "qualityMode": "CONSTANT_QUALITY" if cq else "AVG_BITRATE",
        "quality": float(p["VideoQualitySlider"]),
        "bitrateKbps": int(p["VideoAvgBitrate"]) or 4000,
        "twoPass": bool(p.get("VideoMultiPass")),
        "speed": speed_of(enc, p["VideoPreset"]),
        "profile": p["VideoProfile"] if x26x and p["VideoProfile"] else "auto",
        "level": p["VideoLevel"] if x26x and p["VideoLevel"] else "auto",
        "tune": "none",
        "extraOptions": p.get("x264Option", "") if enc == "x264" else "",
        "audioTracks": "ALL" if p["AudioTrackSelectionBehavior"] == "all" else "FIRST",
        "audioEncoder": audio_encoder,
        "audioMixdown": mix if not pass_mask else "AUTO",
        "audioBitrate": bitrate,
        "audioPassthruMatching": True,
        "subtitles": "ALL" if p["SubtitleTrackSelectionBehavior"] == "all" else "NONE",
        "maxWidth": p["PictureWidth"],
        "maxHeight": p["PictureHeight"],
        "deinterlace": FILTER_DEINT.get(p["PictureDeinterlaceFilter"], "OFF"),
    }
    # 2-pass only makes sense with a target bitrate
    if cq:
        s["twoPass"] = False
    return {
        "name": p["PresetName"],
        "category": cat,
        "description": p.get("PresetDescription", ""),
        "settings": s,
        "builtIn": True,
    }


def android_hw():
    base = {"container": "MP4", "chapterMarkers": True, "framerate": 0, "framerateMode": "PEAK",
            "qualityMode": "AVG_BITRATE", "audioEncoder": "AAC", "audioMixdown": "STEREO", "audioBitrate": 160,
            "audioTracks": "FIRST", "audioPassthruMatching": True, "subtitles": "NONE"}
    out = []
    for enc, tag, rates in (("H264_HW", "H.264", {2160: 20000, 1080: 8000, 720: 4000}),
                            ("H265_HW", "H.265", {2160: 12000, 1080: 5000, 720: 2500})):
        for h, w, br in ((2160, 3840, rates[2160]), (1080, 1920, rates[1080]), (720, 1280, rates[720])):
            name = f"Hardware {tag} {h}p" + (" 4K" if h == 2160 else "")
            out.append({"name": name, "category": "Hardware (Android)",
                        "description": f"Fast {tag} video (up to {h}p) encoded by the phone's hardware encoder, AAC stereo audio, MP4 container.",
                        "settings": {**base, "videoEncoder": enc, "bitrateKbps": br, "maxWidth": w, "maxHeight": h},
                        "builtIn": True})
    return out


def main(src, dst):
    data = json.load(open(src))
    presets = []

    def walk(items, cat=None):
        for p in items:
            if p.get("Folder"):
                walk(p["ChildrenArray"], p["PresetName"])
            else:
                c = convert(p, cat)
                if c:
                    presets.append(c)

    walk(data)
    presets += android_hw()
    json.dump({"presets": presets}, open(dst, "w"), indent=1, ensure_ascii=False)
    print(f"{len(presets)} presets written to {dst}")


if __name__ == "__main__":
    main(*sys.argv[1:3])
