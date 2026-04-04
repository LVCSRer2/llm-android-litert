package com.example.gemma4

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorder {
    private val sampleRate = 16000
    private val bufSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096) * 4

    private var audioRecord: AudioRecord? = null
    @Volatile private var recording = false
    private val pcmBuffer = ByteArrayOutputStream()

    val isRecording get() = recording

    fun start() {
        if (recording) return
        pcmBuffer.reset()
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )
        audioRecord?.startRecording()
        recording = true

        Thread {
            val buf = ByteArray(bufSize)
            while (recording) {
                val read = audioRecord?.read(buf, 0, buf.size) ?: break
                if (read > 0) pcmBuffer.write(buf, 0, read)
            }
        }.start()
    }

    fun stop(): ByteArray {
        recording = false
        Thread.sleep(100) // 마지막 버퍼 flush 대기
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        return toWav(pcmBuffer.toByteArray())
    }

    private fun toWav(pcm: ByteArray): ByteArray {
        val dataSize = pcm.size
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)               // PCM
        header.putShort(1)               // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)    // byteRate
        header.putShort(2)               // blockAlign
        header.putShort(16)              // bitsPerSample
        header.put("data".toByteArray())
        header.putInt(dataSize)
        return header.array() + pcm
    }
}
