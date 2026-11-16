package com.example.guitartuner.audio

import android.media.AudioRecord
import com.example.guitartuner.dsp.YinDetector
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 音频采集 + 音高检测流水线（运行于独立后台线程，不阻塞主线程）。
 *
 * 链路：AudioRecord 读取 → RMS 静音门限 → YIN 检测 → 指数平滑 → 稳定度 → 回调。
 * 检测帧缓冲在循环外复用，避免频繁对象分配（满足性能指标）。
 *
 * 【平台碎片化风险】
 *  - 麦克风增益 / AGC 差异：用 RMS 门限过滤静音；必要时可在检测前做 RMS 归一化；
 *  - 部分设备 PCM Float 初始化失败：由 recorder.start() 抛出，引擎捕获后回传空读数。
 */
class AudioTunerEngine(
    private val sampleRate: Int = AudioRecorder.SAMPLE_RATE,
    detector: YinDetector = YinDetector(sampleRate),
    private val stabilityWindow: Int = STABILITY_WINDOW
) {

    /** 单次检测输出（供 ViewModel 消费）。 */
    data class Reading(
        val frequency: Float,   // Hz；0 表示无有效信号
        val confidence: Float,  // YIN 置信度 0..1
        val stability: Float    // 稳定度 0..1（基于历史频率抖动）
    )

    companion object {
        /** 静音 RMS 门限：Float PCM 归一化 [-1,1]，0.004 ≈ -48dBFS。 */
        private const val MIN_RMS = 0.004f
        private const val STABILITY_WINDOW = 5
    }

    private val recorder = AudioRecorder(sampleRate)
    private val detector = detector
    private var callback: ((Reading) -> Unit)? = null

    @Volatile private var running = false
    private var thread: Thread? = null

    private val history = ArrayDeque<Float>() // 稳定度计算窗口
    private var smoothed = 0f                 // EMA 平滑频率

    /** 启动采集与检测循环。回调在后台线程触发，由调用方负责切换回主线程。 */
    fun start(onReading: (Reading) -> Unit) {
        if (running) return
        callback = onReading
        running = true
        thread = Thread({ loop() }, "tuner-engine").apply {
            isDaemon = true
            start()
        }
    }

    /** 停止采集并释放 AudioRecord。 */
    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        recorder.stop()
        history.clear()
        smoothed = 0f
    }

    private fun loop() {
        val record = try {
            recorder.start()
        } catch (e: Exception) {
            callback?.invoke(Reading(0f, 0f, 0f))
            running = false
            return
        }
        val frames = recorder.framesPerBuffer()
        val buf = FloatArray(frames)   // read 目标缓冲
        val frame = FloatArray(frames) // 检测帧（复用，避免分配）

        while (running) {
            val read = record.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
            if (read <= 0) continue
            System.arraycopy(buf, 0, frame, 0, read)
            val rms = rms(frame, read)
            val result = detector.detect(frame, read)
            if (result != null && rms >= MIN_RMS) {
                val freq = smooth(result.frequency)
                val stability = computeStability(freq)
                callback?.invoke(Reading(freq, result.confidence, stability))
            } else {
                // 未检测到有效信号 → 通知"等待声音"
                callback?.invoke(Reading(0f, 0f, 0f))
            }
        }
        recorder.stop()
    }

    private fun rms(buf: FloatArray, len: Int): Float {
        var sum = 0.0
        for (i in 0 until len) {
            val v = buf[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / len).toFloat()
    }

    /** 指数滑动平均：抑制单帧抖动，又不引入明显滞后。 */
    private fun smooth(freq: Float): Float {
        smoothed = if (smoothed <= 0f) freq else smoothed * 0.7f + freq * 0.3f
        return smoothed
    }

    /** 稳定度：历史频率相对当前值的音分标准差越小越稳定，映射到 0..1。 */
    private fun computeStability(freq: Float): Float {
        history.addLast(freq)
        while (history.size > stabilityWindow) history.removeFirst()
        if (history.size < 3) return 0f
        val cents = DoubleArray(history.size)
        for (i in history.indices) {
            cents[i] = 1200.0 * log2(history.elementAt(i) / freq.toDouble())
        }
        val mean = cents.average()
        val variance = cents.sumOf { (it - mean) * (it - mean) } / cents.size
        val std = sqrt(variance)
        return (1.0 - min(1.0, std / 10.0)).toFloat()
    }
}
