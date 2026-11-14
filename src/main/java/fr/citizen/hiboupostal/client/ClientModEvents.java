package fr.citizen.hiboupostal.client;

import fr.citizen.hiboupostal.HibouPostal;
import fr.citizen.hiboupostal.menu.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Enregistrement des écrans de menu — chargé uniquement côté client. */
@EventBusSubscriber(modid = HibouPostal.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.PARCEL.get(), ParcelScreen::new);
        event.register(ModMenus.MAILBOX.get(), MailboxScreen::new);
    }
}
