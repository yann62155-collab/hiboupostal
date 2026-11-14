package fr.citizen.hiboupostal.menu;

import fr.citizen.hiboupostal.HibouPostal;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HibouPostal.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ParcelMenu>> PARCEL =
            MENUS.register("parcel", () -> IMenuTypeExtension.create(ParcelMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MailboxMenu>> MAILBOX =
            MENUS.register("mailbox", () -> IMenuTypeExtension.create(MailboxMenu::new));

    private ModMenus() {}
}
