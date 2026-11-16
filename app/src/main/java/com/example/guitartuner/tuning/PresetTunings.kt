package com.example.guitartuner.tuning

import com.example.guitartuner.dsp.PitchUtils

/**
 * 常用调弦预设（内置、只读、不可删除）。
 * 在标准 EADGBE 之外提供：Drop D、Drop C、半音降（Eb）、Open G。
 */
object PresetTunings {

    private fun build(id: String, name: String, midis: List<Int>): InstrumentConfig {
        val strings = midis.mapIndexed { index, midi ->
            val note = PitchUtils.midiToNoteName(midi.toDouble())
            TuningString(label = "${midis.size - index}弦", noteName = note, midiNote = midi)
        }
        return InstrumentConfig(id = id, name = name, strings = strings)
    }

    /** Drop D (DADGBE)：D2 A2 D3 G3 B3 E4 */
    val DROP_D = build("preset_drop_d", "Drop D (DADGBE)", listOf(38, 45, 50, 55, 59, 64))

    /** Drop C (CGCFAD)：C2 G2 C3 F3 A3 D4 */
    val DROP_C = build("preset_drop_c", "Drop C (CGCFAD)", listOf(36, 43, 48, 53, 57, 62))

    /** 半音降 (Eb)：Eb2 Ab2 Db3 Gb3 Bb3 Eb4 */
    val HALF_STEP_DOWN = build("preset_half_down", "半音降 (Eb)", listOf(39, 44, 49, 54, 58, 63))

    /** Open G (DGDGBD)：D2 G2 D3 G3 B3 D4 */
    val OPEN_G = build("preset_open_g", "Open G (DGDGBD)", listOf(38, 43, 50, 55, 59, 62))

    val all: List<InstrumentConfig> = listOf(DROP_D, DROP_C, HALF_STEP_DOWN, OPEN_G)
}
