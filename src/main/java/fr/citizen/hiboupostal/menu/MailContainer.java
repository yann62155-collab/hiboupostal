package fr.citizen.hiboupostal.menu;

import fr.citizen.hiboupostal.data.OwlPostData;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Conteneur branché directement sur la boîte aux lettres stockée dans OwlPostData.
 * Le courrier livré pendant que la boîte est ouverte apparaît immédiatement
 * (aucune copie, donc aucun risque d'écraser une livraison).
 */
public class MailContainer implements Container {
    private final OwlPostData data;
    private final NonNullList<ItemStack> items;

    public MailContainer(OwlPostData data, NonNullList<ItemStack> items) {
        this.data = data;
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        setChanged();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public void setChanged() {
        data.markDirty();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false; // seuls les hiboux déposent du courrier
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }
}
