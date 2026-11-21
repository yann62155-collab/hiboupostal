package fr.citizen.hiboupostal.client;

import fr.citizen.hiboupostal.HibouPostal;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enregistrement au micro et lecture des beuglantes (client PC uniquement).
 * <p>
 * Sortie toujours en 16 kHz mono ; transport compressé en µ-law (G.711, 1 octet par échantillon,
 * soit ~1,9 Mo pour 2 minutes). N'utilise que javax.sound (fourni avec Java) : aucune dépendance.
 * <p>
 * Pour que l'enregistrement marche sur un maximum de micros :
 * <ul>
 *   <li>on essaie les formats que chaque micro déclare (8 à 192 kHz, 8/16/24/32 bits, entier ou flottant,
 *       mono ou stéréo) et on convertit nous-mêmes en 16 kHz mono (passe-bas + rééchantillonnage) ;</li>
 *   <li>en mode automatique, si le micro par défaut refuse, on essaie les autres périphériques ;</li>
 *   <li>le joueur peut choisir son micro, qui est testé dès qu'il le sélectionne ;</li>
 *   <li>on détecte un micro muet (accès au micro bloqué par Windows, micro coupé…) ;</li>
 *   <li>à l'arrêt, on récupère ce qui restait dans la mémoire tampon du micro (rien n'est coupé).</li>
 * </ul>
 */
public final class BeuglanteAudio {
    private BeuglanteAudio() {}

    public static final int SAMPLE_RATE = 16000;
    private static final AudioFormat OUT_PCM = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private static final float[] RATES = {16000f, 48000f, 44100f, 32000f, 22050f, 96000f, 8000f};

    // ==================================================================
    // Choix du micro
    // ==================================================================

    /** Index du micro choisi dans {@link #captureDevices()} ; -1 = automatique (micro par défaut puis les autres). */
    private static int selectedDevice = -1;
    /** Nom du micro réellement utilisé au dernier enregistrement. */
    private static volatile String usedDevice = "";

    /** Périphériques capables d'enregistrer (les "Port" de Windows sont exclus). */
    public static List<Mixer.Info> captureDevices() {
        List<Mixer.Info> list = new ArrayList<>();
        Line.Info target = new Line.Info(TargetDataLine.class);
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(mi).isLineSupported(target)) list.add(mi);
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    /** Passe au micro suivant ; renvoie son nom. */
    public static String nextDevice() {
        List<Mixer.Info> devices = captureDevices();
        selectedDevice++;
        if (selectedDevice >= devices.size()) selectedDevice = -1;
        return deviceName();
    }

    public static String deviceName() {
        Mixer.Info mi = selectedMixer();
        return mi == null ? "Automatique" : mi.getName();
    }

    public static String usedDevice() {
        return usedDevice;
    }

    private static Mixer.Info selectedMixer() {
        List<Mixer.Info> devices = captureDevices();
        return selectedDevice >= 0 && selectedDevice < devices.size() ? devices.get(selectedDevice) : null;
    }

    /**
     * Teste le micro choisi (ouverture puis fermeture immédiate).
     * Renvoie null s'il fonctionne, sinon un court message d'erreur.
     */
    public static String testSelectedDevice() {
        Opened o = openAny(selectedMixer());
        if (o == null) return "Ce périphérique refuse l'enregistrement.";
        try {
            o.line.close();
        } catch (Exception ignored) {
        }
        return null;
    }

    // ==================================================================
    // Recherche d'un format accepté par le micro
    // ==================================================================

    private record Opened(TargetDataLine line, AudioFormat format, String device) {}

    /**
     * Ouvre le micro donné (null = automatique : micro par défaut du système, puis chaque
     * périphérique de capture l'un après l'autre).
     */
    private static Opened openAny(Mixer.Info only) {
        if (only != null) {
            return openOn(AudioSystem.getMixer(only), only.getName());
        }
        Opened o = openOn(null, "Micro par défaut");
        if (o != null) return o;
        for (Mixer.Info mi : captureDevices()) {
            o = openOn(AudioSystem.getMixer(mi), mi.getName());
            if (o != null) return o;
        }
        return null;
    }

    /** Essaie les formats possibles sur un périphérique (null = périphérique par défaut). */
    private static Opened openOn(Mixer mixer, String name) {
        for (AudioFormat fmt : candidateFormats(mixer)) {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
            TargetDataLine l = null;
            try {
                boolean supported = mixer == null ? AudioSystem.isLineSupported(info) : mixer.isLineSupported(info);
                if (!supported) continue;
                l = (TargetDataLine) (mixer == null ? AudioSystem.getLine(info) : mixer.getLine(info));
                // tampon d'environ 0,5 s : évite les pertes si le jeu ralentit
                l.open(fmt, Math.max(fmt.getFrameSize(), (int) (fmt.getSampleRate() * 0.5f) * fmt.getFrameSize()));
                return new Opened(l, fmt, name);
            } catch (Exception e) {
                lastOpenError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (l != null) {
                    try {
                        l.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static volatile String lastOpenError = "";

    /**
     * Formats à essayer : d'abord ceux qui ne demandent aucune conversion (16 bits),
     * puis tous ceux que le périphérique déclare (8, 16, 24 ou 32 bits, entier ou flottant,
     * mono ou stéréo), y compris pour le matériel audio "pro" qui n'accepte que du 24 / 32 bits.
     */
    private static List<AudioFormat> candidateFormats(Mixer mixer) {
        Map<String, AudioFormat> out = new LinkedHashMap<>();
        for (float rate : RATES) {
            for (int ch = 1; ch <= 2; ch++) {
                add(out, new AudioFormat(rate, 16, ch, true, false));
            }
        }
        Line.Info[] infos;
        try {
            infos = mixer == null
                    ? AudioSystem.getTargetLineInfo(new Line.Info(TargetDataLine.class))
                    : mixer.getTargetLineInfo();
        } catch (Exception e) {
            infos = new Line.Info[0];
        }
        for (Line.Info li : infos) {
            if (!(li instanceof DataLine.Info dli)) continue;
            for (AudioFormat f : dli.getFormats()) {
                AudioFormat.Encoding enc = f.getEncoding();
                if (!enc.equals(AudioFormat.Encoding.PCM_SIGNED) && !enc.equals(AudioFormat.Encoding.PCM_UNSIGNED)
                        && !enc.equals(AudioFormat.Encoding.PCM_FLOAT)) continue;
                int bits = f.getSampleSizeInBits();
                if (bits != 8 && bits != 16 && bits != 24 && bits != 32) continue;
                int[] channels = f.getChannels() == AudioSystem.NOT_SPECIFIED ? new int[]{1, 2}
                        : new int[]{f.getChannels()};
                float[] rates = f.getSampleRate() == AudioSystem.NOT_SPECIFIED ? RATES : new float[]{f.getSampleRate()};
                for (int ch : channels) {
                    if (ch < 1 || ch > 8) continue;
                    for (float rate : rates) {
                        if (rate < 8000 || rate > 192000) continue;
                        int frameSize = bits / 8 * ch;
                        add(out, new AudioFormat(enc, rate, bits, ch, frameSize, rate, f.isBigEndian()));
                    }
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    private static void add(Map<String, AudioFormat> out, AudioFormat f) {
        out.putIfAbsent(f.toString(), f);
    }

    // ==================================================================
    // Enregistrement
    // ==================================================================

    private static volatile TargetDataLine line;
    private static volatile Thread recordThread;
    private static volatile boolean recording;
    private static volatile float level;
    private static volatile float peak;
    private static volatile boolean hitMax;
    private static volatile String formatInfo = "";
    private static volatile String captureError;
    private static final ByteArrayOutputStream PCM_BUFFER = new ByteArrayOutputStream();
    private static volatile int maxSeconds = 120;

    /** Démarre l'enregistrement. Renvoie un message d'erreur, ou null si tout va bien. */
    public static synchronized String startRecording(int maxSec) {
        stopRecording();
        stopPreview();
        maxSeconds = maxSec;
        synchronized (PCM_BUFFER) {
            PCM_BUFFER.reset();
        }
        peak = 0;
        level = 0;
        hitMax = false;
        captureError = null;
        lastOpenError = "";

        Opened o = openAny(selectedMixer());
        if (o == null) {
            HibouPostal.LOGGER.warn("Aucun micro utilisable ({}) : {}", deviceName(), lastOpenError);
            return selectedMixer() == null
                    ? "Aucun micro n'accepte d'enregistrer. Vérifiez qu'un micro est branché et autorisé dans Windows."
                    : "Ce périphérique refuse l'enregistrement. Choisissez-en un autre avec le bouton Micro.";
        }

        AudioFormat used = o.format();
        usedDevice = o.device();
        formatInfo = (int) used.getSampleRate() + " Hz, " + used.getSampleSizeInBits() + " bits, "
                + (used.getChannels() == 1 ? "mono" : used.getChannels() + " canaux");
        HibouPostal.LOGGER.info("Beuglante : enregistrement via {} ({})", usedDevice, used);
        final TargetDataLine started = o.line();
        started.start();
        line = started;
        recording = true;
        Thread t = new Thread(() -> recordLoop(started, used), "Hibou-Beuglante-Micro");
        t.setDaemon(true);
        recordThread = t;
        t.start();
        return null;
    }

    private static void recordLoop(TargetDataLine l, AudioFormat fmt) {
        Decoder dec = new Decoder(fmt);
        Converter converter = new Converter(fmt.getSampleRate());
        int frameSize = fmt.getFrameSize();
        byte[] buf = new byte[Math.max(frameSize, (int) (fmt.getSampleRate() * 0.05f) * frameSize)]; // ~50 ms

        try {
            while (recording) {
                int n = l.read(buf, 0, buf.length - (buf.length % frameSize));
                if (n <= 0) continue;
                if (!consume(buf, n, dec, converter)) {
                    hitMax = true;
                    recording = false;
                }
            }
            // On récupère ce qui restait dans le tampon du micro au moment de l'arrêt
            l.stop();
            int avail = l.available();
            while (avail >= frameSize && !hitMax) {
                int n = l.read(buf, 0, Math.min(buf.length - (buf.length % frameSize), avail - (avail % frameSize)));
                if (n <= 0) break;
                if (!consume(buf, n, dec, converter)) hitMax = true;
                avail = l.available();
            }
        } catch (Exception e) {
            captureError = "Le micro s'est interrompu : " + e.getMessage();
            HibouPostal.LOGGER.warn("Erreur pendant l'enregistrement", e);
        } finally {
            recording = false;
            level = 0;
            try {
                l.close();
            } catch (Exception ignored) {
            }
            if (line == l) line = null;
        }
    }

    /** Lit les échantillons bruts du micro, quel que soit leur format, et les ramène en mono 16 bits. */
    static final class Decoder {
        private final int channels;
        private final int bytesPerSample;
        private final int frameSize;
        private final boolean bigEndian;
        private final boolean unsigned;
        private final boolean floating;

        Decoder(AudioFormat f) {
            this.channels = Math.max(1, f.getChannels());
            this.bytesPerSample = f.getSampleSizeInBits() / 8;
            this.frameSize = f.getFrameSize() > 0 ? f.getFrameSize() : bytesPerSample * channels;
            this.bigEndian = f.isBigEndian();
            this.unsigned = f.getEncoding().equals(AudioFormat.Encoding.PCM_UNSIGNED);
            this.floating = f.getEncoding().equals(AudioFormat.Encoding.PCM_FLOAT);
        }

        int frames(int bytes) {
            return bytes / frameSize;
        }

        /** Échantillon mono (moyenne des canaux) de la trame f, à l'échelle 16 bits. */
        double mono(byte[] buf, int f) {
            double sum = 0;
            int base = f * frameSize;
            for (int c = 0; c < channels; c++) {
                sum += sample(buf, base + c * bytesPerSample);
            }
            return sum / channels;
        }

        private double sample(byte[] buf, int off) {
            long v = 0;
            for (int i = 0; i < bytesPerSample; i++) {
                int b = buf[off + (bigEndian ? i : bytesPerSample - 1 - i)] & 0xFF;
                v = (v << 8) | b;
            }
            int bits = bytesPerSample * 8;
            if (floating && bytesPerSample == 4) {
                return Float.intBitsToFloat((int) v) * 32767.0;
            }
            if (unsigned) {
                v -= 1L << (bits - 1);
            } else {
                v = (v << (64 - bits)) >> (64 - bits); // extension du signe
            }
            return bits >= 16 ? v / (double) (1L << (bits - 16)) : v * 256.0;
        }
    }

    /** Convertit un bloc lu au micro en 16 kHz mono et l'ajoute ; false si la durée max est atteinte. */
    static boolean consume(byte[] buf, int n, Decoder dec, Converter conv) {
        int frames = dec.frames(n);
        short[] mono = new short[frames];
        double blockPeak = 0;
        for (int f = 0; f < frames; f++) {
            double s = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, dec.mono(buf, f)));
            mono[f] = (short) Math.round(s);
            blockPeak = Math.max(blockPeak, Math.abs(s));
        }
        float p = (float) (blockPeak / 32768.0);
        level = p;
        if (p > peak) peak = p;

        short[] out = conv.process(mono);
        byte[] bytes = new byte[out.length * 2];
        for (int i = 0; i < out.length; i++) {
            bytes[2 * i] = (byte) out[i];
            bytes[2 * i + 1] = (byte) (out[i] >> 8);
        }
        int maxBytes = maxSeconds * SAMPLE_RATE * 2;
        synchronized (PCM_BUFFER) {
            int room = maxBytes - PCM_BUFFER.size();
            if (room <= 0) return false;
            PCM_BUFFER.write(bytes, 0, Math.min(bytes.length, room - (room % 2)));
            return PCM_BUFFER.size() < maxBytes;
        }
    }

    /** Arrête l'enregistrement en conservant tout ce qui a été capté. */
    public static synchronized void stopRecording() {
        recording = false;
        Thread t = recordThread;
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        recordThread = null;
        TargetDataLine l = line;
        if (l != null) { // le fil ne s'est pas arrêté à temps : on force la fermeture
            try {
                l.close();
            } catch (Exception ignored) {
            }
            line = null;
        }
        level = 0;
    }

    public static boolean isRecording() {
        return recording;
    }

    public static float level() {
        return level;
    }

    /** La durée maximale a-t-elle arrêté l'enregistrement ? */
    public static boolean reachedMax() {
        return hitMax;
    }

    public static String formatInfo() {
        return formatInfo;
    }

    /** Erreur survenue pendant la capture (micro débranché…), ou null. */
    public static String captureError() {
        return captureError;
    }

    /**
     * L'enregistrement semble muet (aucun son capté) : micro coupé, mauvais micro,
     * ou accès au micro refusé par Windows aux applications de bureau.
     */
    public static boolean seemsSilent() {
        return recordedSeconds() >= 1.0f && peak < 0.004f;
    }

    public static float recordedSeconds() {
        synchronized (PCM_BUFFER) {
            return PCM_BUFFER.size() / (float) (SAMPLE_RATE * 2);
        }
    }

    public static void clear() {
        stopRecording();
        stopPreview();
        synchronized (PCM_BUFFER) {
            PCM_BUFFER.reset();
        }
        peak = 0;
        hitMax = false;
        captureError = null;
    }

    /** L'enregistrement courant, compressé en µ-law (null s'il fait moins de 0,2 s). */
    public static byte[] recordedUlaw() {
        byte[] pcm;
        synchronized (PCM_BUFFER) {
            pcm = PCM_BUFFER.toByteArray();
        }
        if (pcm.length < SAMPLE_RATE * 2 / 5) return null;
        byte[] out = new byte[pcm.length / 2];
        for (int i = 0; i < out.length; i++) {
            short s = (short) ((pcm[2 * i] & 0xFF) | (pcm[2 * i + 1] << 8));
            out[i] = linearToUlaw(s);
        }
        return out;
    }

    // ==================================================================
    // Conversion vers 16 kHz mono : passe-bas (anti-repliement) + interpolation linéaire
    // ==================================================================

    static final class Converter {
        private final double ratio;       // échantillons source par échantillon de sortie
        private final boolean filter;
        private final double alpha;
        private double lp1;
        private double lp2;
        private double pos = 0;           // position de lecture relative au bloc courant
        private double prev = 0;
        private boolean hasPrev = false;

        Converter(float sourceRate) {
            this.ratio = sourceRate / SAMPLE_RATE;
            this.filter = sourceRate > SAMPLE_RATE + 1;
            // coupure ~7 kHz, deux filtres du 1er ordre en cascade
            this.alpha = 1.0 - Math.exp(-2.0 * Math.PI * 7000.0 / sourceRate);
        }

        short[] process(short[] in) {
            int n = in.length;
            if (n == 0) return new short[0];
            double[] x = new double[n];
            for (int i = 0; i < n; i++) {
                double v = in[i];
                if (filter) {
                    lp1 += alpha * (v - lp1);
                    lp2 += alpha * (lp1 - lp2);
                    v = lp2;
                }
                x[i] = v;
            }
            if (ratio == 1.0) {
                short[] out = new short[n];
                for (int i = 0; i < n; i++) out[i] = clamp(x[i]);
                return out;
            }
            List<Short> out = new ArrayList<>((int) (n / ratio) + 2);
            if (!hasPrev) {
                prev = x[0];
                hasPrev = true;
            }
            // positions possibles : [-1, n-1) ; l'indice -1 désigne le dernier échantillon du bloc précédent
            while (pos < n - 1) {
                int i0 = (int) Math.floor(pos);
                double frac = pos - i0;
                double a = i0 < 0 ? prev : x[i0];
                double b = x[i0 + 1];
                out.add(clamp(a + (b - a) * frac));
                pos += ratio;
            }
            pos -= n;
            prev = x[n - 1];
            short[] arr = new short[out.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
            return arr;
        }

        private static short clamp(double v) {
            return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(v)));
        }
    }

    // ==================================================================
    // Lecture
    // ==================================================================

    private static volatile SourceDataLine previewLine;
    private static volatile boolean previewStop;

    /** Réécoute de son propre enregistrement (une seule à la fois, arrêtable). */
    public static void preview() {
        stopPreview();
        byte[] ulaw = recordedUlaw();
        if (ulaw == null) return;
        previewStop = false;
        startPlayback(ulaw, 1.0f, true);
    }

    public static void stopPreview() {
        previewStop = true;
        SourceDataLine l = previewLine;
        if (l != null) {
            try {
                l.stop();
                l.flush();
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean isPreviewing() {
        return previewLine != null;
    }

    /** Joue une beuglante reçue au volume donné. */
    public static void play(byte[] ulaw, float gain) {
        startPlayback(ulaw, gain, false);
    }

    private static void startPlayback(byte[] ulaw, float gain, boolean isPreview) {
        if (ulaw == null || ulaw.length == 0 || gain <= 0f) return;
        Thread t = new Thread(() -> {
            SourceDataLine out = null;
            try {
                out = AudioSystem.getSourceDataLine(OUT_PCM);
                out.open(OUT_PCM, SAMPLE_RATE); // tampon de 0,5 s
                out.start();
                if (isPreview) previewLine = out;
                byte[] pcm = new byte[3200]; // 100 ms
                int pos = 0;
                while (pos < ulaw.length) {
                    if (isPreview && previewStop) break;
                    int count = Math.min(pcm.length / 2, ulaw.length - pos);
                    for (int i = 0; i < count; i++) {
                        int s = Math.round(ulawToLinear(ulaw[pos + i]) * gain);
                        s = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
                        pcm[2 * i] = (byte) s;
                        pcm[2 * i + 1] = (byte) (s >> 8);
                    }
                    out.write(pcm, 0, count * 2);
                    pos += count;
                }
                if (!(isPreview && previewStop)) out.drain();
            } catch (Exception e) {
                HibouPostal.LOGGER.warn("Lecture de la beuglante impossible", e);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Exception ignored) {
                    }
                }
                if (isPreview && previewLine == out) previewLine = null;
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
