package fr.citizen.hiboupostal.menu;

import fr.citizen.hiboupostal.item.ModItems;
import fr.citizen.hiboupostal.server.OwlPostService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Comptoir d'expédition : 5 emplacements pour remplir un carton + l'inventaire du joueur.
 * Les objets restent dans un conteneur temporaire ; s'il ferme sans envoyer, ils lui sont rendus.
 */
public class ParcelMenu extends AbstractContainerMenu implements OwlPostService.ParcelSource {
    public static final int SLOTS = 5;
    public static final int MAX_NOTE_LENGTH = 60;

    // Positions (relatives au coin haut-gauche de l'écran du menu)
    public static final int PARCEL_X = 44;
    public static final int PARCEL_Y = 76;
    public static final int INV_X = 8;
    public static final int INV_Y = 122;
    public static final int HOTBAR_Y = 180;

    private final SimpleContainer parcel = new SimpleContainer(SLOTS);
    private final String prefill;
    private final int cartons;

    /** Constructeur client (données envoyées par le serveur à l'ouverture). */
    public ParcelMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInv, extraData.readUtf(64), extraData.readVarInt());
    }

    /** Constructeur serveur. */
    public ParcelMenu(int containerId, Inventory playerInv, String prefill, int cartons) {
        super(ModMenus.PARCEL.get(), containerId);
        this.prefill = prefill;
        this.cartons = cartons;

        for (int i = 0; i < SLOTS; i++) {
            addSlot(new Slot(parcel, i, PARCEL_X + i * 18, PARCEL_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // pas de carton scellé dans un carton (évite les poupées russes)
                    return !stack.is(ModItems.SEALED_PARCEL.get());
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public String prefill() {
        return prefill;
    }

    public int cartons() {
        return cartons;
    }

    @Override
    public boolean isParcelEmpty() {
        return parcel.isEmpty();
    }

    /** Retire tout le contenu du carton (côté serveur, au moment de l'envoi). */
    @Override
    public List<ItemStack> takeAll() {
        List<ItemStack> items = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = parcel.getItem(i);
            items.add(s.copy());
        }
        parcel.clearContent();
        broadcastChanges();
        return items;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < SLOTS) {
                if (!moveItemStackTo(stack, SLOTS, SLOTS + 36, true)) return ItemStack.EMPTY;
            } else {
                if (stack.is(ModItems.SEALED_PARCEL.get()) || !moveItemStackTo(stack, 0, SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            // fermeture sans envoi : on rend les objets
            clearContainer(player, parcel);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }
}
