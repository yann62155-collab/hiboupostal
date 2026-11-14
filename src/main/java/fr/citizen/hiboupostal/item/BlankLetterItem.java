package fr.citizen.hiboupostal.item;

import fr.citizen.hiboupostal.server.OwlPostService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class BlankLetterItem extends Item {
    public BlankLetterItem(Properties properties) {
        super(properties);
    }

    /** Clic droit avec une lettre vierge : ouvre la volière (même effet que /hibou). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            OwlPostService.openOwlery(serverPlayer, "");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Un parchemin vierge, prêt à recevoir votre plume.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("Clic droit ou /hibou pour écrire une missive.").withStyle(ChatFormatting.GOLD));
    }
}
