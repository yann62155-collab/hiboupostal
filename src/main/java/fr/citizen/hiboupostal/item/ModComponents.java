package fr.citizen.hiboupostal.item;

import fr.citizen.hiboupostal.HibouPostal;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, HibouPostal.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LetterContent>> LETTER =
            COMPONENTS.registerComponentType("letter", builder -> builder
                    .persistent(LetterContent.CODEC)
                    .networkSynchronized(LetterContent.STREAM_CODEC));

    /** Étiquette d'un carton scellé (expéditeur, destinataire, petit mot dans "title"). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LetterContent>> PARCEL_LABEL =
            COMPONENTS.registerComponentType("parcel_label", builder -> builder
                    .persistent(LetterContent.CODEC)
                    .networkSynchronized(LetterContent.STREAM_CODEC));

    /** Contenu d'une beuglante : title = cri écrit, body = identifiant de l'enregistrement vocal ("" = aucun). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LetterContent>> BEUGLANTE =
            COMPONENTS.registerComponentType("beuglante", builder -> builder
                    .persistent(LetterContent.CODEC)
                    .networkSynchronized(LetterContent.STREAM_CODEC));

    /** Instant (ms) où une beuglante non ouverte éclate d'elle-même. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> BEUGLANTE_DEADLINE =
            COMPONENTS.registerComponentType("beuglante_deadline", builder -> builder
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG));

    private ModComponents() {}
}
