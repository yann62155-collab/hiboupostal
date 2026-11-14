package fr.citizen.hiboupostal.item;

import fr.citizen.hiboupostal.server.OwlPostService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.List;

/** Carton scellé : clic droit pour l'ouvrir et récupérer son contenu. */
public class SealedParcelItem extends Item {
    public SealedParcelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // ouverture différée d'un tick (voir OwlPostService#scheduleUnpack)
            OwlPostService.scheduleUnpack(serverPlayer, hand, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        LetterContent label = stack.get(ModComponents.PARCEL_LABEL.get());
        if (label == null) {
            return super.getName(stack);
        }
        return Component.literal("Carton de " + label.senderName()).withStyle(ChatFormatting.GOLD);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LetterContent label = stack.get(ModComponents.PARCEL_LABEL.get());
        if (label != null) {
            tooltip.add(Component.literal("De : ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(label.senderName() + " (hibou n°" + LetterContent.formatNumber(label.senderNumber()) + ")").withStyle(ChatFormatting.YELLOW)));
            tooltip.add(Component.literal("À : ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(label.recipientName()).withStyle(ChatFormatting.YELLOW)));
            if (!label.title().isBlank()) {
                tooltip.add(Component.literal("« " + label.title() + " »").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
            }
        }
        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
        contents.copyInto(items);
        tooltip.add(Component.literal("Contenu :").withStyle(ChatFormatting.DARK_RED));
        boolean any = false;
        for (ItemStack s : items) {
            if (s.isEmpty()) continue;
            any = true;
            tooltip.add(Component.literal(" • " + s.getCount() + "× ").withStyle(ChatFormatting.GRAY)
                    .append(s.getHoverName().copy().withStyle(ChatFormatting.WHITE)));
        }
        if (!any) {
            tooltip.add(Component.literal(" (vide)").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.literal("Clic droit pour ouvrir le carton.").withStyle(ChatFormatting.GOLD));
    }
}
