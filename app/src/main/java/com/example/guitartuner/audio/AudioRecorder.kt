package com.example.guitartuner.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/**
 * AudioRecord 封装：44100Hz / 单声道 / PCM Float。
 * 缓冲区取最小缓冲区的 2 倍，在延迟与稳定性之间取得平衡。
 *
 * 【平台碎片化风险】
 *  - 部分机型对 ENCODING_PCM_FLOAT 支持不完整：若 AudioRecord 初始化失败，
 *    应提示用户（本类抛出可读异常，由引擎层兜底），或回退 PCM_16BIT 并做缩放；
 *  - 各厂商麦克风增益 / AGC 差异：静音门限与归一化放在 AudioTunerEngine 处理。
 */
class AudioRecorder(
    private val sampleRate: Int = SAMPLE_RATE
) {
    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val TAG = "AudioRecorder"
    }

    private var record: AudioRecord? = null

    /** 每次读取的帧数（float 采样数）。最小缓冲区字节数 ×2，再折算成 float 个数。 */
    fun framesPerBuffer(): Int {
        val minBytes = AudioRecord.getMinBufferSize(
            sampleRate, CHANNEL_IN, AudioFormat.ENCODING_PCM_FLOAT
        )
        val bytes = if (minBytes <= 0) 2048 else minBytes * 2
        return bytes / Float.SIZE_BYTES
    }

    /** 启动录音，返回已进入录制状态的 AudioRecord。失败抛出可读异常。 */
    fun start(): AudioRecord {
        val frames = framesPerBuffer()
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            CHANNEL_IN,
            AudioFormat.ENCODING_PCM_FLOAT,
            frames * Float.SIZE_BYTES
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException(
                "AudioRecord 初始化失败：可能缺少麦克风权限或设备无麦克风"
            )
        }
        rec.startRecording()
        this.record = rec
        return rec
    }

    /** 停止并释放 AudioRecord。 */
    fun stop() {
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        Log.d(TAG, "AudioRecord 已停止并释放")
    }
}
