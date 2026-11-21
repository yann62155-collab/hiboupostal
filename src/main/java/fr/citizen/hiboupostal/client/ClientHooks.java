package fr.citizen.hiboupostal.client;

import fr.citizen.hiboupostal.item.LetterContent;
import fr.citizen.hiboupostal.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Code exclusivement client. Ne jamais référencer directement depuis le code commun
 * (passer par {@link fr.citizen.hiboupostal.network.ClientBridge}).
 */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openOwlery(Payloads.OpenOwlery payload) {
        Minecraft.getInstance().setScreen(new OwleryScreen(payload));
    }

    public static void handleSendResult(Payloads.SendResult payload) {
        if (Minecraft.getInstance().screen instanceof OwleryScreen screen) {
            screen.onSendResult(payload);
        } else if (Minecraft.getInstance().screen instanceof ParcelScreen screen) {
            screen.onSendResult(payload);
        }
    }

    public static void handleContacts(Payloads.ContactsUpdate payload) {
        if (Minecraft.getInstance().screen instanceof OwleryScreen screen) {
            screen.onContacts(payload);
        }
    }

    public static void openLetter(LetterContent content) {
        Minecraft.getInstance().setScreen(new LetterScreen(content));
    }

    /** Morceaux de voix en cours de réception, par beuglante. */
    private static final Map<Integer, byte[][]> INCOMING = new HashMap<>();
    private static final Map<Integer, Long> INCOMING_SINCE = new HashMap<>();

    /** Une beuglante éclate à proximité : on rassemble la voix, puis on la joue (plus faible avec la distance). */
    public static void playBeuglante(Payloads.PlayBeuglante payload) {
        long now = System.currentTimeMillis();
        INCOMING_SINCE.entrySet().removeIf(e -> {
            boolean stale = now - e.getValue() > 60_000L;
            if (stale) INCOMING.remove(e.getKey());
            return stale;
        });
        if (payload.total() <= 0 || payload.total() > 64 || payload.index() < 0 || payload.index() >= payload.total()) {
            return;
        }
        byte[][] parts = INCOMING.computeIfAbsent(payload.playId(), k -> new byte[payload.total()][]);
        INCOMING_SINCE.putIfAbsent(payload.playId(), now);
        if (parts.length != payload.total()) return;
        parts[payload.index()] = payload.audio();
        for (byte[] p : parts) {
            if (p == null) return; // il manque encore des morceaux
        }
        INCOMING.remove(payload.playId());
        INCOMING_SINCE.remove(payload.playId());

        int size = 0;
        for (byte[] p : parts) size += p.length;
        byte[] audio = new byte[size];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, audio, pos, p.length);
            pos += p.length;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d = Math.sqrt(mc.player.distanceToSqr(payload.x(), payload.y(), payload.z()));
        float distanceGain = (float) Math.max(0.15, 1.0 - d / Math.max(1, payload.range()));
        float volume = mc.options.getSoundSourceVolume(SoundSource.MASTER)
                * mc.options.getSoundSourceVolume(SoundSource.PLAYERS);
        // une beuglante, ça hurle : on pousse un peu le volume
        BeuglanteAudio.play(audio, Math.min(2.5f, 1.8f * distanceGain * volume));
    }
}
