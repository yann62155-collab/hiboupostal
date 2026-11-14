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

/** Beuglante vierge : clic droit ouvre la volière (onglet Beuglante). */
public class BlankBeuglanteItem extends Item {
    public BlankBeuglanteItem(Properties properties) {
        super(properties);
    }

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
        tooltip.add(Component.literal("Une enveloppe rouge qui frémit déjà de colère.").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("Elle hurlera votre message à la face du destinataire").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("et de tous ceux qui l'entourent.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("/hibou → onglet Beuglante").withStyle(ChatFormatting.GOLD));
    }
}
