package com.example.guitartuner.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * 调准反馈：提示音 + 震动（开关持久化到 SharedPreferences）。
 *
 * 提示音用 AudioTrack 合成一段"双音上行"提示（E5 → A5，约 260ms），比系统 ToneGenerator
 * 的单音更柔和、不刺耳；合成波形在构造时完成，运行时零分配。
 *
 * 【平台风险】
 *  - 播放走媒体流（ASSISTANCE_SONIFICATION），可能被麦克风短暂拾取形成回授；
 *    时长短、音量中（0.7），多数调音器同款行为，属可接受；
 *  - 个别设备 AudioTrack 初始化失败：runCatching 静默跳过，不影响调音主功能；
 *  - VibrationEffect 需 API 26+（本项目 minSdk=26，满足）；无振动器设备静默跳过。
 */
class TunerFeedback(context: Context) {

    private val prefs = context.getSharedPreferences("tuner_feedback", Context.MODE_PRIVATE)

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    /** 预先合成的提示音（构造时生成一次）。 */
    private val chime: ShortArray? = runCatching { synthesizeChime() }.getOrNull()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(v) { prefs.edit().putBoolean(KEY_SOUND, v).apply() }

    var vibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(v) { prefs.edit().putBoolean(KEY_VIBRATE, v).apply() }

    /** 进入"已调准"状态时调用（带冷却由调用方控制）。 */
    fun onInTune() {
        if (soundEnabled) {
            chime?.let { playChime(it) }
        }
        if (vibrateEnabled) {
            runCatching {
                vibrator?.vibrate(VibrationEffect.createOneShot(140, 60))
            }
        }
    }

    private fun playChime(samples: ShortArray) {
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            track.setVolume(0.7f)
            track.play()
            // MODE_STATIC 播放为异步，播放完成后延迟释放
            Thread {
                Thread.sleep(450)
                runCatching { track.release() }
            }.apply { isDaemon = true }.start()
        }.onFailure { Log.w(TAG, "调准提示音播放失败", it) }
    }

    private companion object {
        const val TAG = "TunerFeedback"
        const val SAMPLE_RATE = 44100
        const val KEY_SOUND = "sound_enabled"
        const val KEY_VIBRATE = "vibrate_enabled"

        /** 合成"调准"提示音：E5(659.26Hz, 110ms) → A5(880Hz, 150ms)，带起音与指数衰减。 */
        fun synthesizeChime(): ShortArray {
            val total = 260 * SAMPLE_RATE / 1000
            val out = ShortArray(total)
            var pos = 0
            pos = renderNote(out, pos, 659.26, 110) // E5
            renderNote(out, pos, 880.00, 150)       // A5
            return out
        }

        private fun renderNote(out: ShortArray, start: Int, freq: Double, ms: Int): Int {
            val n = ms * SAMPLE_RATE / 1000
            val end = minOf(start + n, out.size)
            for (i in start until end) {
                val j = i - start
                val attack = min(1.0, j / 400.0)      // ~9ms 起音，防爆音
                val decay = exp(-5.0 * j / n)          // 指数衰减，音色柔和
                val v = attack * decay * sin(2.0 * PI * freq * j / SAMPLE_RATE)
                out[i] = (v * 32767 * 0.7).toInt().toShort()
            }
            return end
        }
    }
}
