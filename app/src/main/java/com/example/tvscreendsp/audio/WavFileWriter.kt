package com.example.tvscreendsp.audio

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility class to write PCM audio data to a valid WAV file.
 * 
 * WAV file format specification:
 * - 44-byte header (RIFF + fmt + data chunks)
 * - Little-endian byte order
 * - PCM format (uncompressed)
 */
object WavFileWriter {
    
    private const val HEADER_SIZE = 44
    private const val AUDIO_FORMAT_PCM = 1.toShort()
    private const val BITS_PER_SAMPLE = 16
    
    /**
     * Writes PCM audio data to a WAV file.
     * 
     * @param pcmData Raw PCM audio data (16-bit samples)
     * @param outputFile Target file for WAV output
     * @param sampleRate Sample rate in Hz (e.g., 44100)
     * @param numChannels Number of audio channels (1 for mono, 2 for stereo)
     * @throws IOException if file writing fails
     */
    @Throws(IOException::class)
    fun writePcmToWav(
        pcmData: ByteArray,
        outputFile: File,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        numChannels: Int = AudioConfig.NUM_CHANNELS
    ) {
        // Ensure parent directory exists
        outputFile.parentFile?.mkdirs()
        
        FileOutputStream(outputFile).use { fos ->
            val header = createWavHeader(
                pcmDataLength = pcmData.size,
                sampleRate = sampleRate,
                numChannels = numChannels,
                bitsPerSample = BITS_PER_SAMPLE
            )
            fos.write(header)
            fos.write(pcmData)
        }
    }
    
    /**
     * Creates a 44-byte WAV header for the given audio parameters.
     * 
     * WAV Header Structure:
     * Offset | Size | Description
     * -------|------|------------
     * 0      | 4    | "RIFF" chunk ID
     * 4      | 4    | File size - 8
     * 8      | 4    | "WAVE" format
     * 12     | 4    | "fmt " subchunk ID
     * 16     | 4    | Subchunk1 size (16 for PCM)
     * 20     | 2    | Audio format (1 = PCM)
     * 22     | 2    | Number of channels
     * 24     | 4    | Sample rate
     * 28     | 4    | Byte rate
     * 32     | 2    | Block align
     * 34     | 2    | Bits per sample
     * 36     | 4    | "data" subchunk ID
     * 40     | 4    | Data size
     */
    private fun createWavHeader(
        pcmDataLength: Int,
        sampleRate: Int,
        numChannels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = (numChannels * bitsPerSample / 8).toShort()
        val totalFileSize = pcmDataLength + HEADER_SIZE - 8
        
        val buffer = ByteBuffer.allocate(HEADER_SIZE)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        // RIFF chunk descriptor
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))  // ChunkID
        buffer.putInt(totalFileSize)                        // ChunkSize
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))  // Format
        
        // fmt subchunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))  // Subchunk1ID
        buffer.putInt(16)                                   // Subchunk1Size (16 for PCM)
        buffer.putShort(AUDIO_FORMAT_PCM)                   // AudioFormat (1 = PCM)
        buffer.putShort(numChannels.toShort())              // NumChannels
        buffer.putInt(sampleRate)                           // SampleRate
        buffer.putInt(byteRate)                             // ByteRate
        buffer.putShort(blockAlign)                         // BlockAlign
        buffer.putShort(bitsPerSample.toShort())            // BitsPerSample
        
        // data subchunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))  // Subchunk2ID
        buffer.putInt(pcmDataLength)                        // Subchunk2Size
        
        return buffer.array()
    }
}
