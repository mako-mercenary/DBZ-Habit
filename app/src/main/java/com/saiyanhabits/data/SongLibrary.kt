package com.saiyanhabits.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.saiyanhabits.domain.MiniJson
import com.saiyanhabits.domain.games.Onset
import com.saiyanhabits.domain.games.OnsetDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/** A song for the rhythm game, from assets/music. */
data class Song(val title: String, val file: String) {
    val assetPath: String get() = "music/$file"
}

/**
 * The rhythm game's songs: listed in assets/music/manifest.json, decoded with
 * Android's own decoder (MP3, WAV, OGG, M4A), and analysed for beats once,
 * with the result kept in the app's files.
 */
class SongLibrary(private val context: Context) {

    fun songs(): List<Song> = try {
        val text = context.assets.open("music/manifest.json").bufferedReader().use { it.readText() }
        (MiniJson.parse(text) as List<*>).mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val title = map["title"] as? String ?: return@mapNotNull null
            val file = map["file"] as? String ?: return@mapNotNull null
            Song(title, file)
        }
    } catch (e: Exception) {
        Log.w("Songs", "No music manifest: ${e.message}")
        emptyList()
    }

    /** The beats in [song], from the cache if it was analysed before. [progress] goes from 0 to 1. */
    suspend fun onsets(song: Song, progress: (Float) -> Unit = {}): List<Onset> = withContext(Dispatchers.Default) {
        val size = context.assets.openFd(song.assetPath).use { it.length }
        val cache = File(context.filesDir, "rhythm/${song.file}.$size.$CACHE_VERSION.txt")
        if (cache.isFile) {
            return@withContext cache.readLines().mapNotNull { line ->
                val parts = line.split(" ")
                if (parts.size == 3) Onset(parts[0].toLong(), parts[1].toFloat(), parts[2].toFloat()) else null
            }
        }
        val found = decodeAndDetect(song, progress)
        cache.parentFile?.mkdirs()
        cache.writeText(found.joinToString("\n") { "${it.timeMs} ${it.strength} ${it.pitch}" })
        found
    }

    private suspend fun decodeAndDetect(song: Song, progress: (Float) -> Unit): List<Onset> {
        val extractor = MediaExtractor()
        context.assets.openFd(song.assetPath).use { fd -> extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
        val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        var detector = OnsetDetector(rate)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var mono = FloatArray(0)
        try {
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val read = extractor.readSampleData(buf, 0)
                        if (read < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, read, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val newRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (newRate != rate) { rate = newRate; detector = OnsetDetector(rate) }
                    }
                    outIndex >= 0 -> {
                        val buf = codec.getOutputBuffer(outIndex)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val shorts = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val frames = shorts.remaining() / channels
                        if (mono.size < frames) mono = FloatArray(frames)
                        for (f in 0 until frames) {
                            var sum = 0
                            for (c in 0 until channels) sum += shorts.get()
                            mono[f] = sum / (channels * 32768f)
                        }
                        detector.feed(mono, frames)
                        if (durationUs > 0) progress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
        return detector.onsets()
    }

    private companion object {
        /** Bump when the detector changes, so old caches are ignored. */
        const val CACHE_VERSION = 1
    }
}
