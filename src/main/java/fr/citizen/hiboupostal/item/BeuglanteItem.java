package fr.citizen.hiboupostal.item;

import fr.citizen.hiboupostal.server.Beuglantes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Beuglante scellée. Clic droit : elle éclate et hurle son message.
 * Si on tarde trop à l'ouvrir, elle éclate d'elle-même.
 */
public class BeuglanteItem extends Item {
    public BeuglanteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        LetterContent content = stack.get(ModComponents.BEUGLANTE.get());
        if (content == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // rien n'est ajouté à l'inventaire : on peut consommer directement
            stack.shrink(1);
            Beuglantes.burst(serverPlayer, content);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) return;
        if (level.getGameTime() % 10 != 0) return;
        Long deadline = stack.get(ModComponents.BEUGLANTE_DEADLINE.get());
        LetterContent content = stack.get(ModComponents.BEUGLANTE.get());
        if (deadline == null || content == null) return;

        long left = deadline - System.currentTimeMillis();
        if (left <= 0) {
            stack.shrink(1);
            player.sendSystemMessage(Component.literal("La beuglante n'en pouvait plus d'attendre !")
                    .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            Beuglantes.burst(player, content);
        } else if (left < 10_000 && level.getGameTime() % 20 == 0 && level instanceof ServerLevel server) {
            // elle fume de plus en plus fort
            server.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(),
                    3, 0.2, 0.2, 0.2, 0.01);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        LetterContent c = stack.get(ModComponents.BEUGLANTE.get());
        if (c == null) return super.getName(stack);
        return Component.literal("Beuglante de " + c.senderName()).withStyle(ChatFormatting.RED);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LetterContent c = stack.get(ModComponents.BEUGLANTE.get());
        if (c != null) {
            tooltip.add(Component.literal("De : ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(c.senderName() + " (hibou n°" + LetterContent.formatNumber(c.senderNumber()) + ")")
                            .withStyle(ChatFormatting.YELLOW)));
            if (!c.body().isBlank()) {
                tooltip.add(Component.literal("On entend une voix gronder à l'intérieur...").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            }
        }
        Long deadline = stack.get(ModComponents.BEUGLANTE_DEADLINE.get());
        if (deadline != null && deadline != Long.MAX_VALUE) {
            long left = Math.max(0, (deadline - System.currentTimeMillis()) / 1000);
            tooltip.add(Component.literal("Elle éclatera d'elle-même dans " + left + " s !").withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.literal("Clic droit pour l'ouvrir... tout le monde entendra.").withStyle(ChatFormatting.GOLD));
    }
}
