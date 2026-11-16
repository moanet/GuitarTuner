package com.example.guitartuner.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.guitartuner.audio.AudioRecorder
import com.example.guitartuner.audio.AudioTunerEngine
import com.example.guitartuner.audio.TunerFeedback
import com.example.guitartuner.dsp.PitchUtils
import com.example.guitartuner.dsp.YinDetector
import com.example.guitartuner.tuning.InstrumentConfig
import com.example.guitartuner.tuning.TuningRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round

/** 调音模式。 */
enum class TunerMode { MANUAL, AUTO }

/** 界面状态语义（颜色由它驱动，同时辅以文字/指针位置，不只依赖颜色）。 */
enum class TunerStatus { WAITING, IN_TUNE, CLOSE, OFF }

/** 单次检测的 UI 态（已与目标弦比对）。 */
data class PitchReading(
    val frequency: Float = 0f,
    val noteName: String = "—",
    val targetNote: String = "—",
    val cents: Double = 0.0,
    val confidence: Float = 0f,
    val stability: Float = 0f,
    val detectedStringIndex: Int? = null,
    val locked: Boolean = false,    // 手动模式是否命中 ±50 音分锁定窗口
    val octaveHint: Boolean = false // 自动模式八度跳变 / 识别不稳定提示
) {
    val hasSignal: Boolean get() = frequency > 0f

    companion object {
        fun idle() = PitchReading()
    }
}

/**
 * 调音器状态与逻辑。采集/检测跑在后台线程，UI 状态经 Main 线程更新。
 * 音频处理不阻塞主线程；后台/前台切换由 onAppBackground/onAppForeground 接管。
 * 新增能力：调准提示音/震动反馈（可开关、持久化）、音分历史（驱动"音分走势"曲线）。
 */
class TunerViewModel(
    private val repository: TuningRepository,
    appContext: Context
) : ViewModel() {

    private val engine = AudioTunerEngine(
        sampleRate = AudioRecorder.SAMPLE_RATE,
        detector = YinDetector(AudioRecorder.SAMPLE_RATE)
    )

    private val feedback = TunerFeedback(appContext)

    var tuning by mutableStateOf(repository.standard)
        private set
    var mode by mutableStateOf(TunerMode.MANUAL)

    var selectedStringIndex by mutableStateOf(0)
        private set
    var a4Hz by mutableStateOf(PitchUtils.DEFAULT_A4_HZ)
        private set
    var isListening by mutableStateOf(false)
        private set
    var reading by mutableStateOf(PitchReading.idle())
        private set

    /** 反馈开关（持久化）。 */
    var soundEnabled by mutableStateOf(feedback.soundEnabled)
        private set
    var vibrateEnabled by mutableStateOf(feedback.vibrateEnabled)
        private set

    /** 音分历史（最近约 3 秒，驱动走势曲线）。 */
    var centsHistory by mutableStateOf(listOf<Float>())
        private set

    private var wasListening = false
    private var wasInTune = false

    init {
        // 默认选中 1 弦（最高音 E4），界面更直观
        selectedStringIndex = tuning.strings.lastIndex
    }

    // ---------- 控制 ----------
    fun toggleListening() = if (isListening) stop() else start()

    fun start() {
        isListening = true
        engine.start { r ->
            viewModelScope.launch(Dispatchers.Main) { reading = computeReading(r) }
        }
    }

    fun stop() {
        engine.stop()
        isListening = false
        reading = PitchReading.idle()
        centsHistory = emptyList()
        wasInTune = false
    }

    fun selectString(index: Int) {
        if (index in tuning.strings.indices) selectedStringIndex = index
    }

    fun setA4(v: Double) {
        a4Hz = v.coerceIn(420.0, 460.0)
    }

    // ★ 改名：setSoundEnabled → updateSoundEnabled，避开属性 setter 的 JVM 签名
    fun updateSoundEnabled(v: Boolean) {
        if (soundEnabled == v) return
        soundEnabled = v
        feedback.soundEnabled = v
    }

    // ★ 改名：setVibrateEnabled → updateVibrateEnabled
    fun updateVibrateEnabled(v: Boolean) {
        if (vibrateEnabled == v) return
        vibrateEnabled = v
        feedback.vibrateEnabled = v
    }

    fun selectTuning(id: String) {
        repository.get(id)?.let {
            tuning = it
            selectedStringIndex = it.strings.lastIndex
        }
    }

    fun allTunings(): List<InstrumentConfig> = repository.allTunings()

    fun isBuiltIn(id: String): Boolean = repository.isBuiltIn(id)

    fun saveTuning(config: InstrumentConfig) {
        repository.save(config)
        tuning = config
    }

    /** 保存新建/编辑的自定义调弦；id 为空时自动生成。 */
    fun saveCustom(id: String?, name: String, midis: List<Int>) {
        saveTuning(repository.buildCustom(id, name, midis))
    }

    fun deleteTuning(id: String) {
        if (repository.delete(id)) {
            tuning = repository.standard
            selectedStringIndex = tuning.strings.lastIndex
        }
    }

    /** 切后台：停止采集并释放 AudioRecord；记录原状态以便恢复。 */
    fun onAppBackground() {
        wasListening = isListening
        if (isListening) stop()
    }

    /** 回前台：恢复监听（不自动开始用户未开启的监听）。 */
    fun onAppForeground() {
        if (wasListening) {
            wasListening = false
            start()
        }
    }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }

    // ---------- 检测结果 → UI 状态 ----------
    private fun computeReading(r: AudioTunerEngine.Reading): PitchReading {
        if (r.frequency <= 0f || r.confidence < MIN_CONFIDENCE) {
            updateFeedbackAndHistory(null)
            return PitchReading.idle()
        }
        val freq = r.frequency.toDouble()
        val midi = PitchUtils.frequencyToMidi(freq, a4Hz)
        val measuredName = PitchUtils.midiToNoteName(midi)
        val measuredMidi = round(midi).toInt()

        val result = when (mode) {
            TunerMode.MANUAL -> {
                val target = tuning.strings[selectedStringIndex]
                val targetFreq = tuning.targetFrequency(selectedStringIndex, a4Hz)
                val cents = PitchUtils.centsBetween(freq, targetFreq)
                val inLock = abs(cents) <= MANUAL_LOCK_CENTS
                PitchReading(
                    frequency = r.frequency,
                    noteName = measuredName,
                    targetNote = target.noteName,
                    cents = cents,
                    confidence = r.confidence,
                    stability = r.stability,
                    detectedStringIndex = selectedStringIndex,
                    locked = inLock,
                    octaveHint = !inLock && abs(measuredMidi - target.midiNote) >= 12
                )
            }
            TunerMode.AUTO -> {
                val (idx, cents) = nearestString(freq, a4Hz)
                val confident = abs(cents) < AUTO_CONFIDENT_CENTS
                PitchReading(
                    frequency = r.frequency,
                    noteName = measuredName,
                    targetNote = if (confident) tuning.strings[idx].noteName else "?",
                    cents = cents,
                    confidence = r.confidence,
                    stability = r.stability,
                    detectedStringIndex = idx,
                    locked = confident,
                    octaveHint = !confident
                )
            }
        }
        updateFeedbackAndHistory(result)
        return result
    }

    /**
     * 维护音分历史 + 调准反馈触发。
     * 反馈仅在"进入绿色区间（|音分|≤5 且锁定）"的瞬间触发一次，离开后再进入才重新触发，
     * 避免一直停留在准位上时反复响。
     */
    private fun updateFeedbackAndHistory(result: PitchReading?) {
        if (result != null && result.hasSignal) {
            centsHistory =
                (centsHistory + result.cents.coerceIn(-50.0, 50.0).toFloat()).takeLast(HISTORY_LEN)
        }
        val inTune = result != null && result.hasSignal && result.locked && abs(result.cents) <= 5.0
        if (inTune) {
            if (!wasInTune) feedback.onInTune()
            wasInTune = true
        } else {
            wasInTune = false
        }
    }

    /** 自动模式：找与检测频率最接近的弦，返回 (弦下标, 有符号音分)。 */
    private fun nearestString(freq: Double, a4: Double): Pair<Int, Double> {
        var bestIdx = 0
        var bestAbs = Double.MAX_VALUE
        var bestSigned = 0.0
        tuning.strings.forEachIndexed { i, s ->
            val c = PitchUtils.centsBetween(freq, tuning.targetFrequency(i, a4))
            val a = abs(c)
            if (a < bestAbs) {
                bestAbs = a
                bestIdx = i
                bestSigned = c
            }
        }
        return bestIdx to bestSigned
    }

    companion object {
        /** 手动模式锁定窗口（音分）：±50 内才驱动指针，抑制邻弦/说话声。 */
        private const val MANUAL_LOCK_CENTS = 50.0
        /** 自动模式识别置信窗口（音分）。 */
        private const val AUTO_CONFIDENT_CENTS = 60.0
        /** YIN 置信度门限：过低视为噪声。 */
        private const val MIN_CONFIDENCE = 0.4f
        /** 音分历史长度（约 3 秒 @ 30fps）。 */
        private const val HISTORY_LEN = 90
    }

    class Factory(
        private val repository: TuningRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TunerViewModel(repository, appContext) as T
    }
}