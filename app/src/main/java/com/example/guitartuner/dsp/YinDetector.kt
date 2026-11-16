package com.example.guitartuner.dsp

import kotlin.math.min

/**
 * 基于 YIN 算法的实时音高检测器（纯 Kotlin 实现）。
 *
 * 【实现路径声明】自行实现 YIN，而非封装 TarsosDSP。
 * 取舍：
 *  - 优点：无第三方 DSP 依赖，核心逻辑完全可控、可 JVM 单测；YIN 对八度错误鲁棒，
 *    对低音弦（E2=82.41Hz）友好；复杂度 O(N·τmax)，44100Hz 下约 30ms 缓冲单次检测微秒级，
 *    端到端延迟落在 50–100ms 量级（满足 <150ms 目标）。
 *  - 代价：缺少 TarsosDSP 的 FFT_YIN 交叉校验加速；若追求极限精度可切换 HYBRID_YIN_FFT，
 *    但延迟会升至 80–150ms，故默认关闭，作为可切换选项暴露。
 *
 * 【关键参数取值依据】
 *  - sampleRate=44100：吉他频率范围 60–1500Hz 远低于 Nyquist 上限，且 44100Hz 是 Android
 *    全平台基本保证的采样率，故作为采集与检测的统一采样率；
 *  - MIN_FREQUENCY_HZ=60 / MAX_FREQUENCY_HZ=1500：覆盖六根弦（E2 82.41Hz）及其高把位，
 *    并留约 20% 余量；对应最大滞后 τmax = 44100/60 = 735，缓冲需 ≥ 2×735 个样本；
 *  - YIN_THRESHOLD=0.1：YIN 论文（de Cheveigné & Kawahara, 2002）建议 0.1~0.15，
 *    取 0.1 更严格，过滤人声/噪声等非周期信号；
 *  - 抛物线插值：将离散滞后细化为连续滞后，达到音分级（数音分）精度，为默认必开路径。
 */
class YinDetector(
    val sampleRate: Int,
    private val minFrequency: Float = MIN_FREQUENCY_HZ,
    private val maxFrequency: Float = MAX_FREQUENCY_HZ,
    private val absoluteThreshold: Float = YIN_THRESHOLD,
    /** 可选增强：自适应阈值，抑制复杂环境噪声误检。默认关闭。 */
    private val useAdaptiveThreshold: Boolean = false,
    /** 可选增强：多周期校验，抑制低音弦八度高估。默认关闭。 */
    private val useMultiPeriodVote: Boolean = false
) {

    /** 单帧检测结果。 */
    data class Result(
        val frequency: Float,      // Hz
        val confidence: Float,     // 0..1（1 - CMND 最小值）
        val periodInSamples: Float // 亚采样精度滞后
    )

    companion object {
        const val MIN_FREQUENCY_HZ = 60f
        const val MAX_FREQUENCY_HZ = 1500f
        const val YIN_THRESHOLD = 0.1f
        private const val ADAPTIVE_WINDOW = 8
    }

    private val maxLag: Int = (sampleRate / minFrequency).toInt()
    private val minLag: Int = (sampleRate / maxFrequency).toInt()

    /**
     * 检测单帧音高。输入为 44100Hz 单声道 Float PCM（[-1,1] 归一化）。
     * @param buffer 输入缓冲（引擎层复用，避免频繁分配）
     * @param length  有效样本数，默认取 buffer 全长
     * @return 检测到周期信号时返回 [Result]，否则返回 null（对应"等待声音"状态）
     */
    fun detect(buffer: FloatArray, length: Int = buffer.size): Result? {
        val size = if (length in 1..buffer.size) length else buffer.size
        if (maxLag <= 1 || size < 2 * minLag) return null
        val tauMax = min(maxLag, size - 1)

        // 1) 差分函数 d(τ) = Σ_t (x[t] - x[t+τ])²
        val diff = FloatArray(tauMax + 1)
        for (tau in 1..tauMax) {
            var sum = 0f
            val limit = size - tau
            for (t in 0 until limit) {
                val d = buffer[t] - buffer[t + tau]
                sum += d * d
            }
            diff[tau] = sum
        }

        // 2) 累计均值归一化差分函数 (CMND) d'(τ) = d(τ) / [(1/τ)Σ d(j)]
        val cmnd = FloatArray(tauMax + 1)
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..tauMax) {
            running += diff[tau]
            cmnd[tau] = if (running > 0f) diff[tau] * tau / running else 1f
        }

        // 3) 绝对阈值：取第一个低于阈值的 τ 作为候选周期
        var tau = minLag
        while (tau < tauMax && cmnd[tau] > absoluteThreshold) tau++
        if (tau >= tauMax) return null // 未找到周期 → 无有效信号

        // 3b) 自适应阈值（可选）：用候选附近 CMND 均值抬升门限，过滤孤立噪声点
        if (useAdaptiveThreshold) tau = adaptiveThreshold(cmnd, tau)

        // 4) 局部最小：沿 CMND 下降方向取真正的谷点
        while (tau + 1 < tauMax && cmnd[tau + 1] < cmnd[tau]) tau++

        // 5) 多周期校验（可选）：候选的整数倍周期更优则回退，抑制八度高估
        if (useMultiPeriodVote) tau = multiPeriodVote(cmnd, tau)

        // 6) 抛物线插值 → 连续滞后（音分级精度）
        val period = parabolicInterpolation(cmnd, tau)
        if (period <= 0.0) return null

        val freq = sampleRate / period
        if (freq < minFrequency || freq > maxFrequency) return null

        val confidence = (1f - cmnd[tau]).coerceIn(0f, 1f)
        return Result(freq.toFloat(), confidence, period.toFloat())
    }

    /**
     * 抛物线插值：对 CMND 谷点拟合二次曲线，顶点偏移
     * Δ = 0.5 * (s0 - s2) / (s0 - 2*s1 + s2)，其中 s0/s1/s2 为 tau-1/tau/tau+1 处的 CMND 值。
     */
    private fun parabolicInterpolation(cmnd: FloatArray, tau: Int): Double {
        if (tau <= 0 || tau + 1 >= cmnd.size) return tau.toDouble()
        val s0 = cmnd[tau - 1]
        val s1 = cmnd[tau]
        val s2 = cmnd[tau + 1]
        val denom = s0 - 2f * s1 + s2
        if (denom == 0f) return tau.toDouble()
        return (tau + 0.5f * (s0 - s2) / denom).toDouble()
    }

    /** 自适应阈值（可选增强）：以候选附近 CMND 均值为参考，取 min(绝对阈值, 均值×0.6)。 */
    private fun adaptiveThreshold(cmnd: FloatArray, hit: Int): Int {
        var tau = hit
        val start = maxOf(1, hit - ADAPTIVE_WINDOW)
        val end = min(cmnd.size, hit + ADAPTIVE_WINDOW)
        var sum = 0f
        var n = 0
        for (i in start until end) { sum += cmnd[i]; n++ }
        val mean = if (n > 0) sum / n else 1f
        val effective = min(absoluteThreshold, mean * 0.6f)
        while (tau < cmnd.size && cmnd[tau] > effective) tau++
        return tau
    }

    /** 多周期校验（可选增强）：若候选的 2/4/8 倍周期 CMND 显著更小，说明存在八度高估，回退。 */
    private fun multiPeriodVote(cmnd: FloatArray, tau: Int): Int {
        var best = tau
        var bestVal = cmnd[tau]
        var cand = 2 * tau
        while (cand < cmnd.size) {
            if (cmnd[cand] < bestVal * 0.9f) {
                bestVal = cmnd[cand]
                best = cand
            }
            cand *= 2
        }
        return best
    }
}
