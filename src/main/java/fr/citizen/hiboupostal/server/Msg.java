package fr.citizen.hiboupostal.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Messages privés au style médiéval (envoyés uniquement au joueur concerné).
 */
public final class Msg {
    private Msg() {}

    /** Préfixe "✦ Volière ✦" doré. */
    public static MutableComponent prefix() {
        return Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Volière").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.literal(" ✦ ").withStyle(ChatFormatting.GOLD));
    }

    public static MutableComponent text(String s) {
        return Component.literal(s).withStyle(ChatFormatting.GRAY);
    }

    public static MutableComponent name(String s) {
        return Component.literal(s).withStyle(ChatFormatting.GOLD);
    }

    public static MutableComponent number(int n) {
        return Component.literal("n°" + String.format("%04d", n)).withStyle(ChatFormatting.YELLOW);
    }

    public static MutableComponent italic(String s) {
        return Component.literal(s).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

    public static MutableComponent flavor(String s) {
        return Component.literal(s).withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);
    }

    public static MutableComponent error(String s) {
        return prefix().append(Component.literal(s).withStyle(ChatFormatting.RED));
    }

    /**
     * Commande à taper, en évidence. Sur PC un clic la pré-remplit dans le chat ;
     * sur console (pas de clic dans le chat Bedrock) elle reste lisible telle quelle.
     */
    public static MutableComponent command(String cmd) {
        String suggest = cmd.contains("<") ? cmd.substring(0, cmd.indexOf('<')) : cmd;
        return Component.literal(cmd).withStyle(Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggest))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Cliquer pour écrire la commande"))));
    }

    /** Bouton cliquable qui exécute une commande. */
    public static MutableComponent button(String label, String command, String hover) {
        return Component.literal("[" + label + "]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }
}
