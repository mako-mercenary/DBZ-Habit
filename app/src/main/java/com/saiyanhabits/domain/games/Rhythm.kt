package com.saiyanhabits.domain.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A note to hit: when (ms into the song) and in which of the four lanes. */
data class Note(val timeMs: Long, val lane: Int)

/** How hard a chart is: how many of the beats become notes, and how close together they may be. */
enum class RhythmLevel(val title: String, val caption: String, val keepShare: Float, val minGapMs: Long) {
    EASY("Easy", "1x gravity", 0.4f, 420),
    NORMAL("Normal", "10x gravity", 0.7f, 260),
    HARD("Hard", "100x gravity", 1f, 150),
}

/** How well a tap landed. */
enum class Judgement(val label: String, val windowMs: Long, val points: Int) {
    PERFECT("Perfect", 50, 300),
    GREAT("Great", 100, 200),
    GOOD("Good", 150, 100),
    MISS("Miss", Long.MAX_VALUE, 0),
}

/** Scoring rules. Pure functions, no Android. */
object RhythmScoring {
    const val LANES = 4

    /** Judges a tap [deltaMs] from its note (either side). */
    fun judge(deltaMs: Long): Judgement {
        val d = abs(deltaMs)
        return Judgement.entries.first { d <= it.windowMs }
    }

    /** The combo multiplier: grows by one every 25 hits in a row, up to 4x. */
    fun multiplier(combo: Int): Int = (1 + combo / 25).coerceAtMost(4)

    /** Gravity shown for a combo: 1x, rising to 100x. */
    fun gravity(combo: Int): Int = when {
        combo >= 100 -> 100
        combo >= 50 -> 50
        combo >= 25 -> 20
        combo >= 10 -> 10
        else -> 1
    }
}

/**
 * Finds the beats in a song: feed it the audio (mono, any sample rate) in
 * chunks, then ask for the onsets. It works on spectral flux: how much louder
 * each frequency got from one short frame to the next, summed. Sharp rises are
 * drum hits and note starts.
 */
class OnsetDetector(sampleRate: Int) {
    /** Audio is averaged down to about 11 kHz first; beats don't need more. */
    private val decimation = (sampleRate / 11025).coerceAtLeast(1)
    private val rate = sampleRate.toFloat() / decimation
    private val buffer = FloatArray(FRAME)
    private var filled = 0
    private var acc = 0f
    private var accCount = 0
    private var prev = FloatArray(FRAME / 2)
    private val window = FloatArray(FRAME) { (0.5 - 0.5 * cos(2 * PI * it / (FRAME - 1))).toFloat() }
    private val flux = ArrayList<Float>()
    private val centroid = ArrayList<Float>()

    /** Seconds between analysis frames. */
    val hopSeconds: Float get() = HOP / rate

    fun feed(samples: FloatArray, count: Int = samples.size) {
        for (i in 0 until count) {
            acc += samples[i]
            if (++accCount < decimation) continue
            buffer[filled++] = acc / accCount
            acc = 0f
            accCount = 0
            if (filled == FRAME) {
                analyse()
                // Keep the second half for the overlapping next frame.
                System.arraycopy(buffer, HOP, buffer, 0, FRAME - HOP)
                filled = FRAME - HOP
            }
        }
    }

    private fun analyse() {
        val re = FloatArray(FRAME) { buffer[it] * window[it] }
        val im = FloatArray(FRAME)
        fft(re, im)
        val mags = FloatArray(FRAME / 2) { sqrt(re[it] * re[it] + im[it] * im[it]) }
        var f = 0f
        var weighted = 0f
        var total = 0f
        for (k in 1 until mags.size) {
            val rise = mags[k] - prev[k]
            if (rise > 0) {
                f += rise
                weighted += rise * k
                total += rise
            }
        }
        flux += f
        centroid += if (total > 0) weighted / total / mags.size else 0f
        prev = mags
    }

    /**
     * The onsets found: time in ms, strength (0 to 1, relative to the
     * loudest), and where the sound sits from bass (0) to treble (1).
     */
    fun onsets(): List<Onset> {
        if (flux.isEmpty()) return emptyList()
        val max = flux.max().takeIf { it > 0f } ?: return emptyList()
        val norm = flux.map { it / max }
        val out = ArrayList<Onset>()
        for (i in norm.indices) {
            val lo = (i - 12).coerceAtLeast(0)
            val hi = (i + 12).coerceAtMost(norm.lastIndex)
            var sum = 0f
            for (j in lo..hi) sum += norm[j]
            val threshold = 1.4f * sum / (hi - lo + 1) + 0.04f
            val v = norm[i]
            if (v <= threshold) continue
            // A local peak: the highest within two frames either side.
            if ((maxOf(0, i - 2)..minOf(norm.lastIndex, i + 2)).any { it != i && norm[it] > v }) continue
            out += Onset(((i * HOP + FRAME / 2) / rate * 1000).toLong(), v, centroid[i])
        }
        return out
    }

    companion object {
        const val FRAME = 512
        const val HOP = 256

        /** In-place radix-2 FFT. [re] and [im] must be a power of two long. */
        fun fft(re: FloatArray, im: FloatArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) {
                    var t = re[i]; re[i] = re[j]; re[j] = t
                    t = im[i]; im[i] = im[j]; im[j] = t
                }
            }
            var len = 2
            while (len <= n) {
                val ang = -2 * PI / len
                val wr = cos(ang).toFloat()
                val wi = sin(ang).toFloat()
                var i = 0
                while (i < n) {
                    var cr = 1f
                    var ci = 0f
                    for (k in 0 until len / 2) {
                        val ur = re[i + k]
                        val ui = im[i + k]
                        val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                        val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                        re[i + k] = ur + vr
                        im[i + k] = ui + vi
                        re[i + k + len / 2] = ur - vr
                        im[i + k + len / 2] = ui - vi
                        val nr = cr * wr - ci * wi
                        ci = cr * wi + ci * wr
                        cr = nr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}

/** A beat found in the audio. */
data class Onset(val timeMs: Long, val strength: Float, val pitch: Float)

/** Turns onsets into a chart. Pure functions, no Android. */
object ChartMaker {

    /** Notes for [level]: the strongest share of the beats, spaced out, in lanes from bass (left) to treble (right). */
    fun make(onsets: List<Onset>, level: RhythmLevel, leadInMs: Long = 1500): List<Note> {
        if (onsets.isEmpty()) return emptyList()
        val cut = onsets.map { it.strength }.sortedDescending()
            .let { it[((it.size - 1) * level.keepShare).toInt().coerceIn(0, it.lastIndex)] }
        val kept = ArrayList<Onset>()
        for (o in onsets.filter { it.strength >= cut && it.timeMs >= leadInMs }.sortedBy { it.timeMs }) {
            val last = kept.lastOrNull()
            if (last == null || o.timeMs - last.timeMs >= level.minGapMs) {
                kept += o
            } else if (o.strength > last.strength) {
                kept[kept.lastIndex] = o
            }
        }
        // Lanes by pitch, split at the quartiles so all four get used.
        val sorted = kept.map { it.pitch }.sorted()
        fun quartile(q: Float) = sorted[((sorted.size - 1) * q).toInt()]
        val bounds = listOf(quartile(0.25f), quartile(0.5f), quartile(0.75f))
        val notes = ArrayList<Note>()
        var run = 0
        for (o in kept) {
            var lane = bounds.count { o.pitch > it }
            val previous = notes.lastOrNull()?.lane
            run = if (previous == lane) run + 1 else 0
            // Never more than three in a row in one lane.
            if (run >= 3) {
                lane = (lane + 1 + (o.timeMs / 7 % 2).toInt()) % RhythmScoring.LANES
                run = 0
            }
            notes += Note(o.timeMs, lane)
        }
        return notes
    }
}
