package fr.citizen.hiboupostal.network;

import fr.citizen.hiboupostal.HibouPostal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Tous les paquets réseau du mod. (Les joueurs console n'en reçoivent jamais : voir OwlPostService#sendToModded.)
 */
public final class Payloads {
    private Payloads() {}

    /** Petit utilitaire pour écrire les codecs sans répétition. */
    private static <T> StreamCodec<RegistryFriendlyByteBuf, T> codec(BiConsumer<RegistryFriendlyByteBuf, T> enc,
                                                                    Function<RegistryFriendlyByteBuf, T> dec) {
        return new StreamCodec<>() {
            @Override
            public T decode(RegistryFriendlyByteBuf buf) {
                return dec.apply(buf);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, T value) {
                enc.accept(buf, value);
            }
        };
    }

    /** Une entrée du carnet de contacts. */
    public record DirectoryEntry(int number, String name, boolean online) {}

    private static void writeEntries(RegistryFriendlyByteBuf buf, List<DirectoryEntry> list) {
        buf.writeVarInt(list.size());
        for (DirectoryEntry e : list) {
            buf.writeVarInt(e.number());
            buf.writeUtf(e.name(), 64);
            buf.writeBoolean(e.online());
        }
    }

    private static List<DirectoryEntry> readEntries(RegistryFriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), 1024);
        List<DirectoryEntry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new DirectoryEntry(buf.readVarInt(), buf.readUtf(64), buf.readBoolean()));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Serveur -> client : ouvrir la volière
    // ------------------------------------------------------------------
    public record OpenOwlery(int myNumber, int blankLetters, int beuglantes, int maxTitle, int maxBody,
                             int maxBeuglanteSeconds, String prefill,
                             List<DirectoryEntry> contacts) implements CustomPacketPayload {
        public static final Type<OpenOwlery> TYPE = new Type<>(HibouPostal.id("open_owlery"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenOwlery> STREAM_CODEC = codec(
                (buf, p) -> {
                    buf.writeVarInt(p.myNumber());
                    buf.writeVarInt(p.blankLetters());
                    buf.writeVarInt(p.beuglantes());
                    buf.writeVarInt(p.maxTitle());
                    buf.writeVarInt(p.maxBody());
                    buf.writeVarInt(p.maxBeuglanteSeconds());
                    buf.writeUtf(p.prefill(), 64);
                    writeEntries(buf, p.contacts());
                },
                buf -> new OpenOwlery(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), readEntries(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Serveur -> client : carnet de contacts mis à jour
    // ------------------------------------------------------------------
    public record ContactsUpdate(List<DirectoryEntry> contacts, String message) implements CustomPacketPayload {
        public static final Type<ContactsUpdate> TYPE = new Type<>(HibouPostal.id("contacts_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ContactsUpdate> STREAM_CODEC = codec(
                (buf, p) -> {
                    writeEntries(buf, p.contacts());
                    buf.writeUtf(p.message(), 256);
                },
                buf -> new ContactsUpdate(readEntries(buf), buf.readUtf(256)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Serveur -> client : résultat d'un envoi
    // ------------------------------------------------------------------
    public record SendResult(boolean success, String message, int blankLetters) implements CustomPacketPayload {
        public static final Type<SendResult> TYPE = new Type<>(HibouPostal.id("send_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SendResult> STREAM_CODEC = codec(
                (buf, p) -> {
                    buf.writeBoolean(p.success());
                    buf.writeUtf(p.message(), 512);
                    buf.writeVarInt(p.blankLetters());
                },
                buf -> new SendResult(buf.readBoolean(), buf.readUtf(512), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Serveur -> client : une beuglante éclate près de vous (voix)
    // ------------------------------------------------------------------
    public record PlayBeuglante(double x, double y, double z, int range, String sender,
                                byte[] audio) implements CustomPacketPayload {
        public static final Type<PlayBeuglante> TYPE = new Type<>(HibouPostal.id("play_beuglante"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayBeuglante> STREAM_CODEC = codec(
                (buf, p) -> {
                    buf.writeDouble(p.x());
                    buf.writeDouble(p.y());
                    buf.writeDouble(p.z());
                    buf.writeVarInt(p.range());
                    buf.writeUtf(p.sender(), 64);
                    buf.writeByteArray(p.audio());
                },
                buf -> new PlayBeuglante(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(),
                        buf.readUtf(64), buf.readByteArray(600_000)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Client -> serveur : demander l'ouverture de la volière (bouton Répondre)
    // ------------------------------------------------------------------
    public record RequestOwlery(String prefill) implements CustomPacketPayload {
        public static final Type<RequestOwlery> TYPE = new Type<>(HibouPostal.id("request_owlery"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestOwlery> STREAM_CODEC = codec(
                (buf, p) -> buf.writeUtf(p.prefill(), 64),
                buf -> new RequestOwlery(buf.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Client -> serveur : envoyer une lettre
    // ------------------------------------------------------------------
    public record SendLetter(String recipient, String title, String body) implements CustomPacketPayload {
        public static final Type<SendLetter> TYPE = new Type<>(HibouPostal.id("send_letter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SendLetter> STREAM_CODEC = codec(
                (buf, p) -> {
                    buf.writeUtf(p.recipient(), 64);
                    buf.writeUtf(p.title(), 256);
                    buf.writeUtf(p.body(), 8192);
                },
                buf -> new SendLetter(buf.readUtf(64), buf.readUtf(256), buf.readUtf(8192)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Client -> serveur : ouvrir le comptoir d'expédition (carton)
    // ------------------------------------------------------------------
    public record RequestParcel(String prefill) implements CustomPacketPayload {
        public static final Type<RequestParcel> TYPE = new Type<>(HibouPostal.id("request_parcel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestParcel> STREAM_CODEC = codec(
                (buf, p) -> buf.writeUtf(p.prefill(), 64),
                buf -> new RequestParcel(buf.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Client -> serveur : expédier le carton en cours de préparation
    // ------------------------------------------------------------------
    public record SendParcel(String recipient, String note) implements CustomPacketPayload {
        public static final Type<SendParcel> TYPE = new Type<>(HibouPostal.id("send_parcel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SendParcel> STREAM_CODEC = codec(
                (buf, p) -> {
                    buf.writeUtf(p.recipient(), 64);
                    buf.writeUtf(p.note(), 256);
                },
                buf -> new SendParcel(buf.readUtf(64), buf.readUtf(256)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Client -> serveur : ajouter / retirer un contact
    // ------------------------------------------------------------------
    public record AddContact(int number, String nickname) implements CustomPacketPayload {
        public static final Type<AddContact> TYPE = new Type<>(HibouPostal.id("add_contact"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AddContact> STREAM_CODEC = codec(
                (buf, p) -> {
                    buf.writeVarInt(p.number());
                    buf.writeUtf(p.nickname(), 64);
                },
                buf -> new AddContact(buf.readVarInt(), buf.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RemoveContact(int number) implements CustomPacketPayload {
        public static final Type<RemoveContact> TYPE = new Type<>(HibouPostal.id("remove_contact"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveContact> STREAM_CODEC = codec(
                (buf, p) -> buf.writeVarInt(p.number()),
                buf -> new RemoveContact(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Client -> serveur : un morceau d'enregistrement vocal de beuglante
    // ------------------------------------------------------------------
    public record BeuglanteChunk(int uploadId, int index, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<BeuglanteChunk> TYPE = new Type<>(HibouPostal.id("beuglante_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BeuglanteChunk> STREAM_CODEC = codec(
                (buf, p) -> {
                    buf.writeVarInt(p.uploadId());
                    buf.writeVarInt(p.index());
                    buf.writeVarInt(p.total());
                    buf.writeByteArray(p.data());
                },
                buf -> new BeuglanteChunk(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(30_000)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Client -> serveur : envoyer une beuglante (uploadId = -1 : sans voix)
    // ------------------------------------------------------------------
    public record SendBeuglante(String recipient, String cry, int uploadId) implements CustomPacketPayload {
        public static final Type<SendBeuglante> TYPE = new Type<>(HibouPostal.id("send_beuglante"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SendBeuglante> STREAM_CODEC = codec(
                (buf, p) -> {
                    buf.writeUtf(p.recipient(), 64);
                    buf.writeUtf(p.cry(), 256);
                    buf.writeVarInt(p.uploadId());
                },
                buf -> new SendBeuglante(buf.readUtf(64), buf.readUtf(256), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
