package com.example.guitartuner.tuning

/**
 * 内置标准调弦 EADGBE。
 * 六根弦标准频率（A4=440 基准）：E2=82.41, A2=110.00, D3=146.83,
 * G3=196.00, B3=246.94, E4=329.63（对应 MIDI 40/45/50/55/59/64）。
 */
object StandardTunings {

    val EADGBE = InstrumentConfig(
        id = "std_eadgbe",
        name = "标准调弦 (EADGBE)",
        strings = listOf(
            TuningString(label = "6(E)", noteName = "E2", midiNote = 40),
            TuningString(label = "5(A)", noteName = "A2", midiNote = 45),
            TuningString(label = "4(D)", noteName = "D3", midiNote = 50),
            TuningString(label = "3(G)", noteName = "G3", midiNote = 55),
            TuningString(label = "2(B)", noteName = "B3", midiNote = 59),
            TuningString(label = "1(E)", noteName = "E4", midiNote = 64),
        )
    )
}
