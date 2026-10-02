package com.galandras12.handdroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.galandras12.handdroid.R
import com.galandras12.handdroid.data.Deinterlace
import com.galandras12.handdroid.data.FramerateMode
import com.galandras12.handdroid.data.Mixdown
import com.galandras12.handdroid.data.QualityMode
import com.galandras12.handdroid.data.Rotation
import com.galandras12.handdroid.data.Strength
import com.galandras12.handdroid.data.TrackMode

@Composable fun TrackMode.label() = stringResource(when (this) {
    TrackMode.NONE -> R.string.track_none
    TrackMode.FIRST -> R.string.track_first
    TrackMode.ALL -> R.string.track_all
})

@Composable fun Rotation.label() = stringResource(when (this) {
    Rotation.NONE -> R.string.rot_none
    Rotation.CW90 -> R.string.rot_cw90
    Rotation.ROT180 -> R.string.rot_180
    Rotation.CCW90 -> R.string.rot_ccw90
    Rotation.HFLIP -> R.string.rot_hflip
})

@Composable fun Deinterlace.label() = stringResource(when (this) {
    Deinterlace.OFF -> R.string.off
    Deinterlace.DECOMB -> R.string.deint_decomb
    Deinterlace.YADIF -> R.string.deint_yadif
    Deinterlace.BWDIF -> R.string.deint_bwdif
})

@Composable fun Strength.label() = stringResource(when (this) {
    Strength.OFF -> R.string.off
    Strength.LIGHT -> R.string.strength_light
    Strength.MEDIUM -> R.string.strength_medium
    Strength.STRONG -> R.string.strength_strong
})

@Composable fun Mixdown.label() = stringResource(when (this) {
    Mixdown.AUTO -> R.string.mix_auto
    Mixdown.MONO -> R.string.mix_mono
    Mixdown.STEREO -> R.string.mix_stereo
    Mixdown.SURROUND_5_1 -> R.string.mix_51
})

@Composable fun FramerateMode.label() = stringResource(when (this) {
    FramerateMode.PEAK -> R.string.fps_peak
    FramerateMode.CONSTANT -> R.string.fps_constant
    FramerateMode.VARIABLE -> R.string.fps_variable
})

@Composable fun QualityMode.label() = stringResource(when (this) {
    QualityMode.CONSTANT_QUALITY -> R.string.quality_constant
    QualityMode.AVG_BITRATE -> R.string.quality_bitrate
})
