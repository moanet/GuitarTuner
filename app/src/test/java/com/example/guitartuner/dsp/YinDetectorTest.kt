package com.example.guitartuner.dsp

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * YIN 检测器单测（纯 JVM，无需设备）。
 * 覆盖：A4=440Hz 识别、E2=82.41Hz 低音弦识别且无八度误判、G3=196Hz 识别。
 */
class YinDetectorTest {

    private val sampleRate = 44100

    private fun sine(freq: Double, n: Int): FloatArray {
        val buf = FloatArray(n)
        for (i in 0 until n) {
            buf[i] = (0.5 * sin(2.0 * PI * freq * i / sampleRate)).toFloat()
        }
        return buf
    }

    @Test
    fun detectsA4_440Hz() {
        val detector = YinDetector(sampleRate)
        val r = detector.detect(sine(440.0, 4096))
        assertNotNull("应能检测到 440Hz", r)
        assertTrue("440Hz 检测偏差过大", r != null && abs(r.frequency - 440f) < 2f)
        assertTrue("440Hz 置信度应很高", r != null && r.confidence > 0.9f)
    }

    @Test
    fun detectsE2_82_41Hz_withoutOctaveError() {
        val detector = YinDetector(sampleRate)
        val r = detector.detect(sine(82.41, 4096))
        assertNotNull("应能检测到低音 E2", r)
        // 不允许八度误判：结果应落在 70~95Hz，而非翻倍到 ~164Hz
        assertTrue("低音 E2 出现八度误判", r != null && r.frequency in 70f..95f)
        assertTrue("E2 置信度应较高", r != null && r.confidence > 0.9f)
    }

    @Test
    fun detectsG3_196Hz() {
        val detector = YinDetector(sampleRate)
        val r = detector.detect(sine(196.0, 4096))
        assertNotNull("应能检测到 196Hz", r)
        assertTrue("196Hz 检测偏差过大", r != null && abs(r.frequency - 196f) < 2f)
    }
}
