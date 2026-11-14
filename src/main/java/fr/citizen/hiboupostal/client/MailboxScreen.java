package fr.citizen.hiboupostal.client;

import fr.citizen.hiboupostal.menu.MailboxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Écran de la boîte aux lettres, style parchemin. */
public class MailboxScreen extends AbstractContainerScreen<MailboxMenu> {

    public MailboxScreen(MailboxMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 184;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Parchment.draw(g, leftPos, topPos, imageWidth, imageHeight);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            boolean mailSlot = i < MailboxMenu.SIZE;
            g.fill(x - (mailSlot ? 1 : 0), y - (mailSlot ? 1 : 0),
                    x + 18 + (mailSlot ? 1 : 0), y + 18 + (mailSlot ? 1 : 0),
                    mailSlot ? Parchment.GOLD : 0xFF8B7350);
            g.fill(x, y, x + 18, y + 18, 0xFF3A2A18);
            g.fill(x + 1, y + 1, x + 17, y + 17, mailSlot ? 0xFF7A5A34 : 0xFF6B5535);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        Component title = Component.literal("✦ Boîte aux lettres ✦");
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, 6, Parchment.CRIMSON, false);

        int count = 0;
        for (int i = 0; i < MailboxMenu.SIZE; i++) {
            if (!menu.slots.get(i).getItem().isEmpty()) count++;
        }
        String info = count == 0
                ? "Nul courrier ne vous attend."
                : "Courrier de " + menu.ownerName() + " : " + count + "/" + MailboxMenu.SIZE;
        g.drawString(font, info, (imageWidth - font.width(info)) / 2, 17,
                count >= MailboxMenu.SIZE ? Parchment.CRIMSON : Parchment.INK_FAINT, false);

        g.drawString(font, "Votre besace", 8, MailboxMenu.INV_Y - 10, Parchment.INK, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
