package fr.citizen.hiboupostal.item;

import fr.citizen.hiboupostal.network.ClientBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class SealedLetterItem extends Item {
    public SealedLetterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        LetterContent content = stack.get(ModComponents.LETTER.get());
        if (content == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide()) {
            ClientBridge.openLetter(content);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        LetterContent content = stack.get(ModComponents.LETTER.get());
        if (content == null) {
            return super.getName(stack);
        }
        return Component.literal("Missive de " + content.senderName()).withStyle(ChatFormatting.GOLD);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LetterContent c = stack.get(ModComponents.LETTER.get());
        if (c == null) {
            tooltip.add(Component.literal("Une lettre dont le sceau ne porte aucune marque.").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.literal("Scellée à la cire rouge").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("De : ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(c.senderName() + " (hibou n°" + LetterContent.formatNumber(c.senderNumber()) + ")").withStyle(ChatFormatting.YELLOW)));
        tooltip.add(Component.literal("À : ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(c.recipientName()).withStyle(ChatFormatting.YELLOW)));
        if (!c.title().isBlank()) {
            tooltip.add(Component.literal("Objet : ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(c.title()).withStyle(ChatFormatting.WHITE)));
        }
        tooltip.add(Component.literal("Clic droit pour briser le sceau et lire.").withStyle(ChatFormatting.GOLD));
    }
}
