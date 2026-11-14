package fr.citizen.hiboupostal.client;

import fr.citizen.hiboupostal.HibouPostal;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;

/**
 * Enregistrement au micro et lecture des beuglantes (client PC uniquement).
 * Format : 16 kHz, mono ; transport compressé en µ-law (G.711, 1 octet par échantillon).
 * N'utilise que javax.sound (fourni avec Java) : aucune dépendance externe.
 */
public final class BeuglanteAudio {
    private BeuglanteAudio() {}

    public static final int SAMPLE_RATE = 16000;
    private static final AudioFormat PCM = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    // ==================================================================
    // Enregistrement
    // ==================================================================

    private static volatile TargetDataLine line;
    private static volatile Thread recordThread;
    private static volatile boolean recording;
    private static volatile float level;
    private static final ByteArrayOutputStream PCM_BUFFER = new ByteArrayOutputStream();
    private static volatile int maxSeconds = 20;

    /** Démarre l'enregistrement. Renvoie un message d'erreur, ou null si tout va bien. */
    public static synchronized String startRecording(int maxSec) {
        stopRecording();
        maxSeconds = maxSec;
        synchronized (PCM_BUFFER) {
            PCM_BUFFER.reset();
        }
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, PCM);
            if (!AudioSystem.isLineSupported(info)) {
                return "Aucun micro compatible n'a été trouvé.";
            }
            TargetDataLine l = (TargetDataLine) AudioSystem.getLine(info);
            l.open(PCM);
            l.start();
            line = l;
        } catch (Exception e) {
            HibouPostal.LOGGER.warn("Micro indisponible", e);
            return "Impossible d'ouvrir le micro : " + e.getMessage();
        }
        recording = true;
        Thread t = new Thread(BeuglanteAudio::recordLoop, "Hibou-Beuglante-Micro");
        t.setDaemon(true);
        recordThread = t;
        t.start();
        return null;
    }

    private static void recordLoop() {
        byte[] buf = new byte[1600]; // 50 ms
        int maxBytes = maxSeconds * SAMPLE_RATE * 2;
        TargetDataLine l = line;
        while (recording && l != null) {
            int n = l.read(buf, 0, buf.length);
            if (n <= 0) continue;
            int peak = 0;
            for (int i = 0; i + 1 < n; i += 2) {
                int s = Math.abs((short) ((buf[i] & 0xFF) | (buf[i + 1] << 8)));
                if (s > peak) peak = s;
            }
            level = peak / 32768f;
            synchronized (PCM_BUFFER) {
                int room = maxBytes - PCM_BUFFER.size();
                if (room <= 0) {
                    recording = false;
                    break;
                }
                PCM_BUFFER.write(buf, 0, Math.min(n, room));
            }
        }
        closeLine();
    }

    public static synchronized void stopRecording() {
        recording = false;
        Thread t = recordThread;
        if (t != null) {
            try {
                t.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        recordThread = null;
        closeLine();
        level = 0;
    }

    private static void closeLine() {
        TargetDataLine l = line;
        line = null;
        if (l != null) {
            try {
                l.stop();
                l.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean isRecording() {
        return recording;
    }

    public static float level() {
        return level;
    }

    public static float recordedSeconds() {
        synchronized (PCM_BUFFER) {
            return PCM_BUFFER.size() / (float) (SAMPLE_RATE * 2);
        }
    }

    public static void clear() {
        stopRecording();
        synchronized (PCM_BUFFER) {
            PCM_BUFFER.reset();
        }
    }

    /** L'enregistrement courant, compressé en µ-law (null s'il est vide). */
    public static byte[] recordedUlaw() {
        byte[] pcm;
        synchronized (PCM_BUFFER) {
            pcm = PCM_BUFFER.toByteArray();
        }
        if (pcm.length < SAMPLE_RATE / 5) return null; // moins de 0,1 s : rien d'utile
        byte[] out = new byte[pcm.length / 2];
        for (int i = 0; i < out.length; i++) {
            short s = (short) ((pcm[2 * i] & 0xFF) | (pcm[2 * i + 1] << 8));
            out[i] = linearToUlaw(s);
        }
        return out;
    }

    // ==================================================================
    // Lecture
    // ==================================================================

    /** Joue un son µ-law dans un fil séparé, au volume donné (0..1). */
    public static void play(byte[] ulaw, float gain) {
        if (ulaw == null || ulaw.length == 0 || gain <= 0f) return;
        Thread t = new Thread(() -> {
            byte[] pcm = new byte[ulaw.length * 2];
            for (int i = 0; i < ulaw.length; i++) {
                int s = Math.round(ulawToLinear(ulaw[i]) * gain);
                s = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
                pcm[2 * i] = (byte) s;
                pcm[2 * i + 1] = (byte) (s >> 8);
            }
            try {
                SourceDataLine out = AudioSystem.getSourceDataLine(PCM);
                out.open(PCM);
                out.start();
                out.write(pcm, 0, pcm.length);
                out.drain();
                out.close();
            } catch (Exception e) {
                HibouPostal.LOGGER.warn("Lecture de la beuglante impossible", e);
            }
        }, "Hibou-Beuglante-Lecture");
        t.setDaemon(true);
        t.start();
    }

    // ==================================================================
    // Codec µ-law (G.711)
    // ==================================================================

    private static final int BIAS = 0x84;
    private static final int CLIP = 32635;

    public static byte linearToUlaw(short sample) {
        int s = sample;
        int sign = (s >> 8) & 0x80;
        if (sign != 0) s = -s;
        if (s > CLIP) s = CLIP;
        s += BIAS;
        int exponent = 7;
        for (int mask = 0x4000; (s & mask) == 0 && exponent > 0; mask >>= 1) {
            exponent--;
        }
        int mantissa = (s >> (exponent + 3)) & 0x0F;
        return (byte) ~(sign | (exponent << 4) | mantissa);
    }

    public static short ulawToLinear(byte ulaw) {
        int u = ~ulaw & 0xFF;
        int sign = u & 0x80;
        int exponent = (u >> 4) & 0x07;
        int mantissa = u & 0x0F;
        int s = ((mantissa << 3) + BIAS) << exponent;
        s -= BIAS;
        return (short) (sign != 0 ? -s : s);
    }
}
