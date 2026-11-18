package com.saiyanhabits.domain

import com.saiyanhabits.domain.games.ChartMaker
import com.saiyanhabits.domain.games.Judgement
import com.saiyanhabits.domain.games.OnsetDetector
import com.saiyanhabits.domain.games.RhythmLevel
import com.saiyanhabits.domain.games.RhythmScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class RhythmTest {

    /** A synthetic drum track: low kicks and high hats at known times, over quiet noise. */
    private fun track(rate: Int, seconds: Int, hits: List<Pair<Long, Boolean>>): FloatArray {
        val out = FloatArray(rate * seconds)
        val noise = Random(1)
        for (i in out.indices) out[i] = (noise.nextFloat() - 0.5f) * 0.01f
        for ((ms, low) in hits) {
            val start = (ms * rate / 1000).toInt()
            for (k in 0 until rate / 8) {
                val t = k.toFloat() / rate
                val env = exp(-t * 30).toFloat()
                val s = if (low) sin(2 * PI * 70 * t).toFloat() else (noise.nextFloat() - 0.5f) * 1.6f
                if (start + k < out.size) out[start + k] += s * env
            }
        }
        return out
    }

    @Test
    fun `the FFT finds a pure tone in the right bin`() {
        val n = 512
        val re = FloatArray(n) { sin(2 * PI * 32 * it / n).toFloat() }
        val im = FloatArray(n)
        OnsetDetector.fft(re, im)
        val mags = (0 until n / 2).map { re[it] * re[it] + im[it] * im[it] }
        assertEquals(32, mags.indices.maxBy { mags[it] })
    }

    @Test
    fun `beats are found where they are, from audio fed in chunks`() {
        val rate = 44100
        val hits = (0 until 16).map { (1000L + it * 500L) to (it % 2 == 0) }
        val audio = track(rate, 10, hits)
        val detector = OnsetDetector(rate)
        audio.toList().chunked(4096).forEach { chunk -> detector.feed(chunk.toFloatArray()) }
        val onsets = detector.onsets()
        for ((ms, _) in hits) {
            assertTrue("missing the hit at $ms ms in ${onsets.map { it.timeMs }}", onsets.any { abs(it.timeMs - ms) <= 40 })
        }
        // And not much else: allow a few stray detections.
        assertTrue("${onsets.size} onsets", onsets.size <= hits.size + 4)
        // Kicks sit lower than hats.
        val kick = onsets.filter { o -> hits.any { (ms, low) -> low && abs(o.timeMs - ms) <= 40 } }.map { it.pitch }.average()
        val hat = onsets.filter { o -> hits.any { (ms, low) -> !low && abs(o.timeMs - ms) <= 40 } }.map { it.pitch }.average()
        assertTrue("kick $kick hat $hat", kick < hat)
    }

    @Test
    fun `charts get busier with difficulty, keep their spacing and use every lane`() {
        val rate = 22050
        val hits = (0 until 60).map { (1600L + it * 180L) to (it % 3 != 0) }
        val detector = OnsetDetector(rate)
        detector.feed(track(rate, 14, hits))
        val onsets = detector.onsets()
        val easy = ChartMaker.make(onsets, RhythmLevel.EASY)
        val hard = ChartMaker.make(onsets, RhythmLevel.HARD)
        assertTrue("easy ${easy.size} hard ${hard.size}", easy.size < hard.size)
        for (level in RhythmLevel.entries) {
            val chart = ChartMaker.make(onsets, level)
            assertTrue(chart.zipWithNext().all { (a, b) -> b.timeMs - a.timeMs >= level.minGapMs })
            assertTrue(chart.all { it.lane in 0 until RhythmScoring.LANES })
        }
        assertEquals((0 until RhythmScoring.LANES).toSet(), hard.map { it.lane }.toSet())
        // No more than three notes in a row in one lane.
        assertTrue(hard.windowed(4).none { w -> w.all { it.lane == w[0].lane } })
    }

    @Test
    fun `taps are judged by how close they land, and combos raise the multiplier`() {
        assertEquals(Judgement.PERFECT, RhythmScoring.judge(-30))
        assertEquals(Judgement.GREAT, RhythmScoring.judge(80))
        assertEquals(Judgement.GOOD, RhythmScoring.judge(-140))
        assertEquals(Judgement.MISS, RhythmScoring.judge(400))
        assertEquals(1, RhythmScoring.multiplier(0))
        assertEquals(2, RhythmScoring.multiplier(25))
        assertEquals(4, RhythmScoring.multiplier(500))
        assertEquals(100, RhythmScoring.gravity(120))
    }
}
