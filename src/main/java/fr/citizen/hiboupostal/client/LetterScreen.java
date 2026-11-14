package fr.citizen.hiboupostal.client;

import fr.citizen.hiboupostal.item.LetterContent;
import fr.citizen.hiboupostal.network.Payloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lecture d'une lettre scellée.
 */
public class LetterScreen extends Screen {
    private static final int W = 250;
    private static final int H = 220;
    private static final int TEXT_TOP = 74;
    private static final int TEXT_BOTTOM = 182;
    private static final int LINE_H = 10;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private final LetterContent letter;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private int left;
    private int top;
    private int scroll = 0;

    public LetterScreen(LetterContent letter) {
        super(Component.literal("Missive"));
        this.letter = letter;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;

        lines.clear();
        for (String paragraph : letter.body().split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            } else {
                lines.addAll(font.split(Component.literal(paragraph), W - 28));
            }
        }
        scroll = Math.min(scroll, maxScroll());

        int bw = (W - 28 - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("✉ Répondre"), b -> reply())
                .bounds(left + 14, top + H - 26, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("★ Carnet"), b -> addContact())
                .bounds(left + 14 + bw + 4, top + H - 26, bw, 18)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Inscrire " + letter.senderName() + " (hibou n°" + LetterContent.formatNumber(letter.senderNumber())
                                + ") dans votre carnet de contacts")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(left + 14 + 2 * (bw + 4), top + H - 26, bw, 18).build());
    }

    private void addContact() {
        PacketDistributor.sendToServer(new Payloads.AddContact(letter.senderNumber(), ""));
    }

    private int visibleLines() {
        return (TEXT_BOTTOM - TEXT_TOP) / LINE_H;
    }

    private int maxScroll() {
        return Math.max(0, lines.size() - visibleLines());
    }

    private void reply() {
        PacketDistributor.sendToServer(new Payloads.RequestOwlery(String.valueOf(letter.senderNumber())));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Parchment.draw(g, left, top, W, H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        String from = "De " + letter.senderName() + "  (hibou n°" + LetterContent.formatNumber(letter.senderNumber()) + ")";
        String to = "À l'attention de " + letter.recipientName();
        String date = "Rédigée le " + DATE.format(Instant.ofEpochMilli(letter.sentAt()).atZone(ZoneId.systemDefault()));

        g.drawString(font, from, left + 14, top + 12, Parchment.CRIMSON, false);
        g.drawString(font, to, left + 14, top + 24, Parchment.INK, false);
        g.drawString(font, date, left + 14, top + 36, Parchment.INK_FAINT, false);
        Parchment.seal(g, left + W - 22, top + 22);

        String subject = letter.title().isBlank() ? "Sans objet" : letter.title();
        Component subj = Component.literal("« " + subject + " »");
        g.drawString(font, subj, left + (W - font.width(subj)) / 2, top + 52, Parchment.INK, false);
        Parchment.rule(g, left + 14, left + W - 14, top + 65);

        int visible = visibleLines();
        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            g.drawString(font, lines.get(scroll + i), left + 14, top + TEXT_TOP + i * LINE_H, Parchment.INK, false);
        }
        if (maxScroll() > 0) {
            // petite barre de défilement dorée
            int barTop = top + TEXT_TOP;
            int barH = TEXT_BOTTOM - TEXT_TOP;
            int thumbH = Math.max(10, barH * visible / lines.size());
            int thumbY = barTop + (barH - thumbH) * scroll / maxScroll();
            g.fill(left + W - 9, barTop, left + W - 8, barTop + barH, Parchment.INK_FAINT);
            g.fill(left + W - 10, thumbY, left + W - 7, thumbY + thumbH, Parchment.GOLD);
        }

        String sign = "— " + letter.senderName();
        g.drawString(font, sign, left + W - 14 - font.width(sign), top + TEXT_BOTTOM + 2, Parchment.CRIMSON, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
