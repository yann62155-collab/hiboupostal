package fr.citizen.hiboupostal.block;

import fr.citizen.hiboupostal.HibouPostal;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HibouPostal.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HibouPostal.MODID);

    /** Boîte aux lettres : reçoit le courrier quand le propriétaire est absent ou que sa besace est pleine. */
    public static final DeferredBlock<MailboxBlock> MAILBOX = BLOCKS.register("mailbox",
            () -> new MailboxBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MailboxBlockEntity>> MAILBOX_BE =
            BLOCK_ENTITIES.register("mailbox",
                    () -> BlockEntityType.Builder.of(MailboxBlockEntity::new, MAILBOX.get()).build(null));

    private ModBlocks() {}
}
