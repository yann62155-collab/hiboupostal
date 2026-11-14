package fr.citizen.hiboupostal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Bloc-entité de la boîte aux lettres : ne stocke que le propriétaire.
 * Le courrier lui-même est stocké dans les données du monde (OwlPostData),
 * il n'est donc jamais perdu si la boîte est cassée.
 */
public class MailboxBlockEntity extends BlockEntity {
    @Nullable
    private UUID owner;
    private String ownerName = "";

    public MailboxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MAILBOX_BE.get(), pos, state);
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("owner", owner);
            tag.putString("owner_name", ownerName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
    }
}
