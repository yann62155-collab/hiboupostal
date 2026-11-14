package fr.citizen.hiboupostal.client;

import fr.citizen.hiboupostal.item.LetterContent;
import fr.citizen.hiboupostal.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

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

    /** Une beuglante éclate à proximité : on joue la voix, plus faible avec la distance. */
    public static void playBeuglante(Payloads.PlayBeuglante payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d = Math.sqrt(mc.player.distanceToSqr(payload.x(), payload.y(), payload.z()));
        float distanceGain = (float) Math.max(0.15, 1.0 - d / Math.max(1, payload.range()));
        float volume = mc.options.getSoundSourceVolume(SoundSource.MASTER)
                * mc.options.getSoundSourceVolume(SoundSource.PLAYERS);
        // une beuglante, ça hurle : on pousse un peu le volume
        BeuglanteAudio.play(payload.audio(), Math.min(2.5f, 1.8f * distanceGain * volume));
    }
}
