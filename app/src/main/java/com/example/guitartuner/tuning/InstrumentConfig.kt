package com.example.guitartuner.tuning

import com.example.guitartuner.dsp.PitchUtils

/**
 * 单根弦的目标音高：以 MIDI 编号为核心，频率按当前 A4 基准实时换算。
 * 采用 MIDI 而非硬编码频率，便于切换 A4（如 432/440Hz）时保持音名不变。
 */
data class TuningString(
    val label: String,     // 界面弦名，如 "6(E)"
    val noteName: String,  // 标准音名，如 "E2"
    val midiNote: Int      // MIDI 编号，E2=40, A2=45, ... E4=64
)

/**
 * 乐器调弦配置（参数化结构，便于扩展至尤克里里、贝斯等乐器）。
 * 弦序按从低到高排列：index 0 = 最粗（最低音）弦。
 * A4 基准不固化在此，由应用级校准状态统一提供，避免多数据源歧义。
 */
data class InstrumentConfig(
    val id: String,
    val name: String,
    val strings: List<TuningString>
) {
    val stringCount: Int get() = strings.size

    /** 按给定 A4 基准计算某根弦的目标频率（Hz）。 */
    fun targetFrequency(index: Int, a4Hz: Double): Double {
        require(index in strings.indices) { "index $index 超出弦数范围" }
        return PitchUtils.midiToFrequency(strings[index].midiNote.toDouble(), a4Hz)
    }
}
