package fr.citizen.hiboupostal;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration serveur (fichier world/serverconfig/hiboupostal-server.toml).
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue DELIVERY_DELAY_SECONDS = BUILDER
            .comment("Temps de vol du hibou avant la remise de la lettre (secondes).")
            .defineInRange("deliveryDelaySeconds", 5, 0, 3600);

    public static final ModConfigSpec.IntValue SEND_COOLDOWN_SECONDS = BUILDER
            .comment("Delai minimum entre deux envois pour un meme joueur (secondes).")
            .defineInRange("sendCooldownSeconds", 10, 0, 3600);

    public static final ModConfigSpec.BooleanValue ALLOW_OFFLINE_DELIVERY = BUILDER
            .comment("Autoriser l'envoi a un joueur hors-ligne (la lettre lui sera remise a sa prochaine connexion).")
            .define("allowOfflineDelivery", true);

    public static final ModConfigSpec.IntValue MAX_TITLE_LENGTH = BUILDER
            .comment("Longueur maximale de l'objet d'une lettre.")
            .defineInRange("maxTitleLength", 40, 8, 64);

    public static final ModConfigSpec.IntValue MAX_BODY_LENGTH = BUILDER
            .comment("Longueur maximale du corps d'une lettre.")
            .defineInRange("maxBodyLength", 1000, 50, 4000);

    public static final ModConfigSpec.IntValue MAX_CONTACTS = BUILDER
            .comment("Nombre maximum de contacts dans le carnet d'un joueur.")
            .defineInRange("maxContacts", 100, 5, 500);

    public static final ModConfigSpec.IntValue SEAL_RANGE = BUILDER
            .comment("Distance maximale (blocs) pour tendre son sceau (son numéro) a un autre joueur avec /hibou sceau.")
            .defineInRange("sealRange", 8, 1, 64);

    // Nouveau nom de clé (l'ancienne "beuglanteMaxSeconds" plafonnait à 30 s) : la valeur 120 s
    // s'applique ainsi aussi aux serveurs qui ont déjà un fichier de configuration.
    public static final ModConfigSpec.IntValue BEUGLANTE_MAX_SECONDS = BUILDER
            .comment("Duree maximale d'enregistrement vocal d'une beuglante (secondes). 120 = 2 minutes.")
            .defineInRange("beuglanteMaxDurationSeconds", 120, 3, 120);

    public static final ModConfigSpec.IntValue BEUGLANTE_RANGE = BUILDER
            .comment("Distance (blocs) a laquelle une beuglante est entendue quand elle eclate.")
            .defineInRange("beuglanteRange", 24, 4, 128);

    public static final ModConfigSpec.IntValue BEUGLANTE_AUTO_OPEN_SECONDS = BUILDER
            .comment("Une beuglante non ouverte eclate d'elle-meme apres ce delai (secondes). 0 = jamais.")
            .defineInRange("beuglanteAutoOpenSeconds", 60, 0, 3600);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
