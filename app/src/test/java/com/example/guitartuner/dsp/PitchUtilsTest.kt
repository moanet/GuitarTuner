package com.example.guitartuner.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * 频率 → 音名 / 音分换算单测（纯 JVM）。
 * 覆盖：A4=440 识别、E2=82.41 低音弦识别、偏离 ±20 音分时符号正确性。
 */
class PitchUtilsTest {

    @Test
    fun recognizesA4_440Hz() {
        assertEquals(69.0, PitchUtils.frequencyToMidi(440.0), 0.01)
        assertEquals("A4", PitchUtils.midiToNoteName(69.0))
    }

    @Test
    fun recognizesE2_82_41Hz() {
        val midi = PitchUtils.frequencyToMidi(82.41)
        assertTrue("E2 的 MIDI 应接近 40", abs(midi - 40.0) < 0.05)
        assertEquals("E2", PitchUtils.midiToNoteName(midi))
    }

    @Test
    fun centsSign_isCorrectForPlusMinus20() {
        val ratio = 2.0.pow(20.0 / 1200.0)
        // 高于目标 → 正（偏高）
        val up = PitchUtils.centsBetween(440.0 * ratio, 440.0)
        assertTrue("偏高 +20 音分应为正", up in 19.5..20.5)
        // 低于目标 → 负（偏低）
        val down = PitchUtils.centsBetween(440.0 / ratio, 440.0)
        assertTrue("偏低 -20 音分应为负", down in -20.5..-19.5)
        // 完全准确 → 0
        assertEquals(0.0, PitchUtils.centsBetween(440.0, 440.0), 0.0001)
    }
}
