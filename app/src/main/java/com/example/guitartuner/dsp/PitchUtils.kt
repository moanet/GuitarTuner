package com.example.guitartuner.dsp
import kotlin.math.pow

/**
 * 频率 → 音名 / 音分 换算（纯 Kotlin，与 UI 解耦，便于 JUnit 单测）。
 * 采用十二平均律（ET），A4 参考频率可调（默认 440Hz，应用内可 420–460 调节）。
 */
object PitchUtils {

    const val DEFAULT_A4_HZ = 440.0
    private const val SEMITONES = 12.0
    private val NOTE_NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )

    /** 频率（Hz）→ MIDI 音符编号（A4=69）。返回小数以保留音分精度。 */
    fun frequencyToMidi(freqHz: Double, a4Hz: Double = DEFAULT_A4_HZ): Double =
        69.0 + SEMITONES * kotlin.math.log2(freqHz / a4Hz)

    /** MIDI 编号 → 音名（含八度），如 69.0 → "A4"，40.0 → "E2"。 */
    fun midiToNoteName(midi: Double): String {
        val rounded = kotlin.math.round(midi).toInt()
        val pitchClass = ((rounded % 12) + 12) % 12
        val octave = rounded / 12 - 1
        return NOTE_NAMES[pitchClass] + octave
    }

    /** 两频率间的音分偏差（正值 = 高于目标 / 偏高，负值 = 偏低）。 */
    fun centsBetween(measuredHz: Double, targetHz: Double): Double =
        if (targetHz > 0) 1200.0 * kotlin.math.log2(measuredHz / targetHz) else 0.0

    /** MIDI 编号 → 频率（Hz）。用于按当前 A4 基准重算弦目标频率。 */
    fun midiToFrequency(midi: Double, a4Hz: Double = DEFAULT_A4_HZ): Double =
        a4Hz * 2.0.pow((midi - 69.0) / SEMITONES)
}
