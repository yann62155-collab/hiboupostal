package fr.citizen.hiboupostal;

import com.mojang.logging.LogUtils;
import fr.citizen.hiboupostal.block.ModBlocks;
import fr.citizen.hiboupostal.item.ModComponents;
import fr.citizen.hiboupostal.item.ModItems;
import fr.citizen.hiboupostal.menu.ModMenus;
import fr.citizen.hiboupostal.network.ModNetwork;
import fr.citizen.hiboupostal.server.OwlCommands;
import fr.citizen.hiboupostal.server.OwlPostService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(HibouPostal.MODID)
public class HibouPostal {
    public static final String MODID = "hiboupostal";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HibouPostal(IEventBus modEventBus, ModContainer modContainer) {
        ModComponents.COMPONENTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);

        modEventBus.addListener(ModNetwork::register);
        modEventBus.addListener(HibouPostal::addCreative);

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        NeoForge.EVENT_BUS.addListener(HibouPostal::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(HibouPostal::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(HibouPostal::onServerTick);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.BLANK_LETTER.get());
            event.accept(ModItems.PARCEL.get());
            event.accept(ModItems.BLANK_BEUGLANTE.get());
            event.accept(ModItems.MAILBOX.get());
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        OwlCommands.register(event.getDispatcher());
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            OwlPostService.onLogin(player);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        OwlPostService.tick(event.getServer());
    }
}
