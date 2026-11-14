package fr.citizen.hiboupostal.block;

import com.mojang.serialization.MapCodec;
import fr.citizen.hiboupostal.data.OwlPostData;
import fr.citizen.hiboupostal.server.Msg;
import fr.citizen.hiboupostal.server.OwlPostService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Boîte aux lettres physique.
 * - Posée par un joueur, elle lui appartient (seul lui peut l'ouvrir et la casser).
 * - Le contenu est commun à toutes les boîtes du même propriétaire.
 */
public class MailboxBlock extends BaseEntityBlock {
    public static final MapCodec<MailboxBlock> CODEC = simpleCodec(MailboxBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public MailboxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // BaseEntityBlock est invisible par défaut
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MailboxBlockEntity(pos, state);
    }

    private static String key(Level level, BlockPos pos) {
        return OwlPostData.posKey(level.dimension().location().toString(), pos.asLong());
    }

    // ------------------------------------------------------------------
    // Pose : la boîte appartient à celui qui la pose
    // ------------------------------------------------------------------

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof ServerPlayer player)) return;
        if (level.getBlockEntity(pos) instanceof MailboxBlockEntity box) {
            box.setOwner(player.getUUID(), player.getGameProfile().getName());
            OwlPostData data = OwlPostData.get(player.server);
            data.getOrAssign(player.getUUID(), player.getGameProfile().getName());
            data.registerMailbox(player.getUUID(), key(level, pos));
            player.sendSystemMessage(Msg.prefix()
                    .append(Msg.text("Vous plantez votre boîte aux lettres. Les hiboux y déposeront votre courrier "
                            + "lorsque vous serez absent ou que votre besace sera pleine.")));
        }
    }

    // ------------------------------------------------------------------
    // Ouverture
    // ------------------------------------------------------------------

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof MailboxBlockEntity box)) {
            return InteractionResult.PASS;
        }

        UUID owner = box.getOwner();
        if (owner == null) {
            // boîte sans propriétaire (posée par commande, WorldEdit...) : le premier qui l'ouvre la réclame
            box.setOwner(serverPlayer.getUUID(), serverPlayer.getGameProfile().getName());
            OwlPostData.get(serverPlayer.server).registerMailbox(serverPlayer.getUUID(), key(level, pos));
            owner = serverPlayer.getUUID();
            serverPlayer.sendSystemMessage(Msg.prefix().append(Msg.text("Cette boîte aux lettres n'avait point de maître : elle est désormais vôtre.")));
        }

        if (!owner.equals(serverPlayer.getUUID())) {
            serverPlayer.sendSystemMessage(Msg.error("Cette boîte aux lettres appartient à " + box.getOwnerName()
                    + ". Le secret des missives est sacré en ce royaume."));
            return InteractionResult.CONSUME;
        }

        OwlPostService.openMailbox(serverPlayer, level, pos);
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------
    // Protection : seul le propriétaire (ou un joueur en créatif) peut la casser
    // ------------------------------------------------------------------

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!player.isCreative() && level.getBlockEntity(pos) instanceof MailboxBlockEntity box
                && box.getOwner() != null && !box.getOwner().equals(player.getUUID())) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof MailboxBlockEntity box && box.getOwner() != null
                && level.getServer() != null) {
            // le courrier reste stocké : il sera accessible depuis une autre boîte du propriétaire
            OwlPostData.get(level.getServer()).unregisterMailbox(box.getOwner(), key(level, pos));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
