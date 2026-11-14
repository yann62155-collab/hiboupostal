package fr.citizen.hiboupostal.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Contenu d'une lettre scellée, stocké en data component sur l'item.
 */
public record LetterContent(
        String senderName,
        int senderNumber,
        String recipientName,
        int recipientNumber,
        String title,
        String body,
        long sentAt
) {
    public static final Codec<LetterContent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("sender_name").forGetter(LetterContent::senderName),
            Codec.INT.fieldOf("sender_number").forGetter(LetterContent::senderNumber),
            Codec.STRING.fieldOf("recipient_name").forGetter(LetterContent::recipientName),
            Codec.INT.fieldOf("recipient_number").forGetter(LetterContent::recipientNumber),
            Codec.STRING.fieldOf("title").forGetter(LetterContent::title),
            Codec.STRING.fieldOf("body").forGetter(LetterContent::body),
            Codec.LONG.fieldOf("sent_at").forGetter(LetterContent::sentAt)
    ).apply(i, LetterContent::new));

    public static final StreamCodec<FriendlyByteBuf, LetterContent> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LetterContent decode(FriendlyByteBuf buf) {
            return new LetterContent(
                    buf.readUtf(64), buf.readVarInt(),
                    buf.readUtf(64), buf.readVarInt(),
                    buf.readUtf(256), buf.readUtf(8192),
                    buf.readLong());
        }

        @Override
        public void encode(FriendlyByteBuf buf, LetterContent c) {
            buf.writeUtf(c.senderName(), 64);
            buf.writeVarInt(c.senderNumber());
            buf.writeUtf(c.recipientName(), 64);
            buf.writeVarInt(c.recipientNumber());
            buf.writeUtf(c.title(), 256);
            buf.writeUtf(c.body(), 8192);
            buf.writeLong(c.sentAt());
        }
    };

    /** Numéro de hibou formaté, ex : 0007. */
    public static String formatNumber(int number) {
        return String.format("%04d", number);
    }
}
