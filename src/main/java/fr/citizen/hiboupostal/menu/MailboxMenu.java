package fr.citizen.hiboupostal.menu;

import fr.citizen.hiboupostal.block.ModBlocks;
import fr.citizen.hiboupostal.data.OwlPostData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu de la boîte aux lettres : 27 emplacements en retrait seul
 * (on y prend son courrier, on ne peut rien y déposer) + l'inventaire du joueur.
 */
public class MailboxMenu extends AbstractContainerMenu {
    public static final int SIZE = OwlPostData.MAILBOX_SIZE;
    public static final int BOX_X = 8;
    public static final int BOX_Y = 30;
    public static final int INV_Y = 102;
    public static final int HOTBAR_Y = 160;

    private final Container mail;
    private final ContainerLevelAccess access;
    private final String ownerName;

    /** Constructeur client. */
    public MailboxMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInv, new SimpleContainer(SIZE), ContainerLevelAccess.NULL, extraData.readUtf(64));
    }

    /** Constructeur serveur. */
    public MailboxMenu(int containerId, Inventory playerInv, Container mail, ContainerLevelAccess access, String ownerName) {
        super(ModMenus.MAILBOX.get(), containerId);
        this.mail = mail;
        this.access = access;
        this.ownerName = ownerName;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(mail, col + row * 9, BOX_X + col * 18, BOX_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false; // seuls les hiboux déposent du courrier
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    public String ownerName() {
        return ownerName;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= SIZE) {
            return ItemStack.EMPTY; // rien ne peut être déposé dans la boîte
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (!moveItemStackTo(stack, SIZE, SIZE + 36, true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.MAILBOX.get());
    }
}
