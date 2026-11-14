package fr.citizen.hiboupostal.client;

import fr.citizen.hiboupostal.menu.ParcelMenu;
import fr.citizen.hiboupostal.network.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Comptoir d'expédition : on dépose jusqu'à 5 piles d'objets dans le carton,
 * on indique le destinataire, et le hibou grand-duc l'emporte.
 */
public class ParcelScreen extends AbstractContainerScreen<ParcelMenu> {
    private EditBox recipientBox;
    private EditBox noteBox;
    private Button sendButton;
    private int cartons;
    private Component status = Component.empty();
    private boolean waiting = false;

    public ParcelScreen(ParcelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 204;
        this.cartons = menu.cartons();
    }

    @Override
    protected void init() {
        String recipient = recipientBox != null ? recipientBox.getValue() : menu.prefill();
        String note = noteBox != null ? noteBox.getValue() : "";
        super.init();

        recipientBox = new EditBox(font, leftPos + 72, topPos + 26, 96, 14, Component.literal("Destinataire"));
        recipientBox.setMaxLength(32);
        recipientBox.setHint(Component.literal("n° ou pseudo").withStyle(ChatFormatting.DARK_GRAY));
        recipientBox.setValue(recipient);
        addRenderableWidget(recipientBox);

        noteBox = new EditBox(font, leftPos + 72, topPos + 44, 96, 14, Component.literal("Petit mot"));
        noteBox.setMaxLength(ParcelMenu.MAX_NOTE_LENGTH);
        noteBox.setHint(Component.literal("facultatif").withStyle(ChatFormatting.DARK_GRAY));
        noteBox.setValue(note);
        addRenderableWidget(noteBox);

        sendButton = addRenderableWidget(Button.builder(Component.literal("✉ Confier le carton au hibou"), b -> send())
                .bounds(leftPos + 8, topPos + 97, 160, 16).build());
        updateSendButton();

        if (recipient.isEmpty()) {
            setInitialFocus(recipientBox);
        }
    }

    private void updateSendButton() {
        sendButton.active = !waiting && cartons > 0;
    }

    private void send() {
        if (recipientBox.getValue().isBlank()) {
            status = Component.literal("Indiquez un destinataire.");
            return;
        }
        if (menu.isParcelEmpty()) {
            status = Component.literal("Le carton est vide.");
            return;
        }
        waiting = true;
        updateSendButton();
        PacketDistributor.sendToServer(new Payloads.SendParcel(recipientBox.getValue().trim(), noteBox.getValue()));
    }

    /** Échec renvoyé par le serveur (en cas de succès, le serveur ferme le menu). */
    public void onSendResult(Payloads.SendResult result) {
        waiting = false;
        cartons = result.blankLetters();
        if (!result.success()) {
            status = Component.literal(result.message());
        }
        updateSendButton();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Quand on tape dans un champ, la touche d'inventaire (E) ne doit pas fermer l'écran.
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
            GuiEventListener focused = getFocused();
            if (focused instanceof EditBox box && box.canConsumeInput()) {
                box.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Parchment.draw(g, leftPos, topPos, imageWidth, imageHeight);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            boolean parcelSlot = i < ParcelMenu.SLOTS;
            g.fill(x - (parcelSlot ? 1 : 0), y - (parcelSlot ? 1 : 0),
                    x + 18 + (parcelSlot ? 1 : 0), y + 18 + (parcelSlot ? 1 : 0),
                    parcelSlot ? Parchment.GOLD : 0xFF8B7350);
            g.fill(x, y, x + 18, y + 18, 0xFF3A2A18);
            g.fill(x + 1, y + 1, x + 17, y + 17, parcelSlot ? 0xFF7A5A34 : 0xFF6B5535);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        Component title = Component.literal("✦ Expédition d'un carton ✦");
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, 6, Parchment.CRIMSON, false);

        if (status.getString().isEmpty()) {
            String info = "Cartons vides : " + cartons;
            g.drawString(font, info, (imageWidth - font.width(info)) / 2, 15, cartons > 0 ? Parchment.INK_FAINT : Parchment.CRIMSON, false);
        } else {
            List<FormattedCharSequence> lines = font.split(status, imageWidth - 16);
            if (!lines.isEmpty()) {
                FormattedCharSequence line = lines.get(0);
                g.drawString(font, line, (imageWidth - font.width(line)) / 2, 15, Parchment.CRIMSON, false);
            }
        }

        g.drawString(font, "Destinataire", 8, 29, Parchment.INK, false);
        g.drawString(font, "Petit mot", 8, 47, Parchment.INK, false);
        String content = "Contenu (5 emplacements)";
        g.drawString(font, content, (imageWidth - font.width(content)) / 2, 64, Parchment.INK, false);
        g.drawString(font, "Votre besace", 8, ParcelMenu.INV_Y - 10, Parchment.INK, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
