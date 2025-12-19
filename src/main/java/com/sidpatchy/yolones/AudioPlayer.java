package com.sidpatchy.yolones;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class AudioPlayer {
    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 735 * 2; // Roughly one frame worth of samples (1470 bytes)
    private SourceDataLine line;
    private byte[] buffer;
    private int bufferIndex;

    public AudioPlayer() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try {
            line = AudioSystem.getSourceDataLine(format);
            // Open with a reasonable internal buffer (e.g., 100ms)
            // 44100 * 0.1 * 2 bytes = 8820 bytes
            line.open(format, 8820);
            line.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
        buffer = new byte[BUFFER_SIZE];
        bufferIndex = 0;
    }

    public void addSample(float sample) {
        if (line == null) return;

        // Convert float sample (-1.0 to 1.0) to 16-bit PCM
        short pcm = (short) (sample * 32767);
        buffer[bufferIndex++] = (byte) (pcm & 0xFF);
        buffer[bufferIndex++] = (byte) ((pcm >> 8) & 0xFF);

        if (bufferIndex >= buffer.length) {
            // write() will block until there is space in the buffer.
            // This provides the natural backpressure for the emulation speed.
            line.write(buffer, 0, buffer.length);
            bufferIndex = 0;
        }
    }

    public int getAvailableBytes() {
        if (line == null) return 0;
        return line.available();
    }

    public int getBufferSize() {
        if (line == null) return 0;
        return line.getBufferSize();
    }

    public void close() {
        if (line != null) {
            line.drain();
            line.stop();
            line.close();
        }
    }

    public int getSampleRate() {
        return SAMPLE_RATE;
    }
}
