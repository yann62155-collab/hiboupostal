package fr.citizen.hiboupostal.server;

import fr.citizen.hiboupostal.Config;
import fr.citizen.hiboupostal.HibouPostal;
import fr.citizen.hiboupostal.item.LetterContent;
import fr.citizen.hiboupostal.network.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Les beuglantes : lettres hurlantes.
 * <ul>
 *   <li>Un cri écrit (lu par tous ceux qui sont à portée, PC comme console).</li>
 *   <li>Un message vocal facultatif, enregistré au micro par les joueurs PC (µ-law 16 kHz mono),
 *       stocké dans le dossier du monde puis joué une seule fois quand la beuglante éclate.</li>
 * </ul>
 */
public final class Beuglantes {
    private Beuglantes() {}

    public static final int SAMPLE_RATE = 16000;
    public static final int MAX_CRY_LENGTH = 100;
    /** Taille d'un morceau envoyé par le client (sous la limite de 32 Ko des paquets client -> serveur). */
    public static final int CHUNK_SIZE = 24000;

    // ------------------------------------------------------------------
    // Réception de l'enregistrement en plusieurs morceaux
    // ------------------------------------------------------------------

    private static final class Upload {
        final int id;
        final byte[][] chunks;
        final long startedAt = System.currentTimeMillis();

        Upload(int id, int total) {
            this.id = id;
            this.chunks = new byte[total][];
        }

        boolean complete() {
            for (byte[] c : chunks) if (c == null) return false;
            return true;
        }

        byte[] assemble() {
            int size = 0;
            for (byte[] c : chunks) size += c.length;
            byte[] out = new byte[size];
            int pos = 0;
            for (byte[] c : chunks) {
                System.arraycopy(c, 0, out, pos, c.length);
                pos += c.length;
            }
            return out;
        }
    }

    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();

    public static int maxBytes() {
        return Config.BEUGLANTE_MAX_SECONDS.get() * SAMPLE_RATE;
    }

    public static void receiveChunk(ServerPlayer player, Payloads.BeuglanteChunk chunk) {
        int maxChunks = maxBytes() / CHUNK_SIZE + 2;
        if (chunk.total() <= 0 || chunk.total() > maxChunks || chunk.index() < 0 || chunk.index() >= chunk.total()
                || chunk.data().length > CHUNK_SIZE) {
            UPLOADS.remove(player.getUUID());
            return;
        }
        Upload up = UPLOADS.get(player.getUUID());
        if (up == null || up.id != chunk.uploadId() || up.chunks.length != chunk.total()) {
            up = new Upload(chunk.uploadId(), chunk.total());
            UPLOADS.put(player.getUUID(), up);
        }
        up.chunks[chunk.index()] = chunk.data();
    }

    /** Récupère l'enregistrement complet envoyé par le joueur, ou null s'il est incomplet. */
    public static byte[] takeUpload(ServerPlayer player, int uploadId) {
        Upload up = UPLOADS.remove(player.getUUID());
        if (up == null || up.id != uploadId || !up.complete()) return null;
        byte[] audio = up.assemble();
        if (audio.length > maxBytes()) {
            byte[] cut = new byte[maxBytes()];
            System.arraycopy(audio, 0, cut, 0, cut.length);
            audio = cut;
        }
        return audio;
    }

    /** Oublie les envois abandonnés depuis plus de 2 minutes. */
    public static void cleanupUploads() {
        long limit = System.currentTimeMillis() - 120_000L;
        UPLOADS.values().removeIf(u -> u.startedAt < limit);
    }

    // ------------------------------------------------------------------
    // Stockage sur disque (dossier du monde/hiboupostal/beuglantes)
    // ------------------------------------------------------------------

    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("hiboupostal").resolve("beuglantes");
    }

    /** Enregistre l'audio et renvoie son identifiant ("" en cas d'échec). */
    public static String store(MinecraftServer server, byte[] audio) {
        String id = UUID.randomUUID().toString();
        try {
            Path d = dir(server);
            Files.createDirectories(d);
            Files.write(d.resolve(id + ".ulaw"), audio);
            return id;
        } catch (IOException e) {
            HibouPostal.LOGGER.error("Impossible d'enregistrer la beuglante", e);
            return "";
        }
    }

    /** Lit puis supprime l'audio (une beuglante ne hurle qu'une fois). */
    private static byte[] consume(MinecraftServer server, String id) {
        if (id == null || id.isBlank() || !id.matches("[0-9a-fA-F-]{36}")) return null;
        Path file = dir(server).resolve(id.toLowerCase(Locale.ROOT) + ".ulaw");
        try {
            if (!Files.exists(file)) return null;
            byte[] data = Files.readAllBytes(file);
            Files.deleteIfExists(file);
            return data;
        } catch (IOException e) {
            HibouPostal.LOGGER.error("Impossible de lire la beuglante {}", id, e);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // L'éclatement
    // ------------------------------------------------------------------

    /**
     * La beuglante éclate au-dessus du destinataire : tous les joueurs à portée
     * lisent le cri (PC et console) et entendent la voix (joueurs PC ayant le mod).
     */
    public static void burst(ServerPlayer recipient, LetterContent c) {
        ServerLevel level = recipient.serverLevel();
        double x = recipient.getX();
        double y = recipient.getY() + 1.6;
        double z = recipient.getZ();
        int range = Config.BEUGLANTE_RANGE.get();
        double rangeSq = (double) range * range;

        level.playSound(null, x, y, z, SoundEvents.GHAST_SCREAM, SoundSource.PLAYERS, 2.5F, 0.7F);
        level.playSound(null, x, y, z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.5F, 1.4F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 25, 0.4, 0.4, 0.4, 0.03);
        level.sendParticles(ParticleTypes.FLAME, x, y + 0.5, z, 15, 0.3, 0.3, 0.3, 0.02);

        String cry = c.title().toUpperCase(Locale.ROOT);
        Component line = Component.literal("‼ ").withStyle(ChatFormatting.RED)
                .append(Component.literal("BEUGLANTE").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.literal(" de " + c.senderName() + " pour " + recipient.getGameProfile().getName() + " : ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("« " + cry + " »").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        // Titre géant sur l'écran du destinataire (fonctionne aussi sur Xbox via Geyser)
        recipient.connection.send(new ClientboundSetTitlesAnimationPacket(5, 70, 20));
        recipient.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("BEUGLANTE !").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        recipient.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(cry.length() > 60 ? cry.substring(0, 57) + "..." : cry).withStyle(ChatFormatting.RED)));

        byte[] audio = consume(recipient.server, c.body());
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) > rangeSq) continue;
            p.sendSystemMessage(line);
            if (audio != null && p.connection.hasChannel(Payloads.PlayBeuglante.TYPE)) {
                PacketDistributor.sendToPlayer(p, new Payloads.PlayBeuglante(x, y, z, range, c.senderName(), audio));
            }
        }
        recipient.sendSystemMessage(Msg.prefix().append(Msg.italic("L'enveloppe rouge se consume en une pluie de cendres.")));
    }
}
