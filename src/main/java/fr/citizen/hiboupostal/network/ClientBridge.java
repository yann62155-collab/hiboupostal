package fr.citizen.hiboupostal.network;

import fr.citizen.hiboupostal.client.ClientHooks;
import fr.citizen.hiboupostal.item.LetterContent;

/**
 * Pont entre le code commun et le code client.
 * Ces méthodes ne sont appelées que côté client : la classe {@link ClientHooks}
 * (qui référence Minecraft/Screen) n'est donc jamais chargée sur un serveur dédié.
 */
public final class ClientBridge {
    private ClientBridge() {}

    public static void openOwlery(Payloads.OpenOwlery payload) {
        ClientHooks.openOwlery(payload);
    }

    public static void handleSendResult(Payloads.SendResult payload) {
        ClientHooks.handleSendResult(payload);
    }

    public static void handleContacts(Payloads.ContactsUpdate payload) {
        ClientHooks.handleContacts(payload);
    }

    public static void playBeuglante(Payloads.PlayBeuglante payload) {
        ClientHooks.playBeuglante(payload);
    }

    public static void openLetter(LetterContent content) {
        ClientHooks.openLetter(content);
    }
}
