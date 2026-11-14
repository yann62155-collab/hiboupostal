package fr.citizen.hiboupostal.server;

import com.mojang.authlib.GameProfile;
import fr.citizen.hiboupostal.block.ModBlocks;
import fr.citizen.hiboupostal.data.OwlPostData;
import fr.citizen.hiboupostal.item.LetterContent;
import fr.citizen.hiboupostal.item.ModComponents;
import fr.citizen.hiboupostal.item.ModItems;
import fr.citizen.hiboupostal.menu.MailContainer;
import fr.citizen.hiboupostal.menu.ParcelMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Interface pour les joueurs console (Xbox / Bedrock via Geyser) et les clients sans le mod.
 * Uniquement des éléments vanilla que Geyser sait traduire :
 * menus de coffre, livres écrits, titres, messages et commandes.
 */
public final class ConsoleUi {
    private ConsoleUi() {}

    private static final int CONTACTS_PER_PAGE = 36;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    // ==================================================================
    // Volière console : coffre de 6 rangées
    // ==================================================================

    public static void openOwlery(ServerPlayer player) {
        openOwleryPage(player, 0);
    }

    private static void openOwleryPage(ServerPlayer player, int page) {
        OwlPostData data = OwlPostData.get(player.server);
        int myNumber = data.getOrAssign(player.getUUID(), player.getGameProfile().getName());
        List<Integer> numbers = new ArrayList<>(data.contacts(player.getUUID()).keySet());
        int pages = Math.max(1, (numbers.size() + CONTACTS_PER_PAGE - 1) / CONTACTS_PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));

        SimpleContainer box = new SimpleContainer(54);
        Map<Integer, Consumer<ServerPlayer>> actions = new HashMap<>();

        // Contacts (rangées 1 à 4)
        for (int i = 0; i < CONTACTS_PER_PAGE; i++) {
            int index = current * CONTACTS_PER_PAGE + i;
            if (index >= numbers.size()) break;
            int number = numbers.get(index);
            Optional<UUID> id = data.findByNumber(number);
            String display = data.contactDisplayName(player.getUUID(), number);
            boolean online = id.isPresent() && player.server.getPlayerList().getPlayer(id.get()) != null;

            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            id.ifPresent(uuid -> head.set(DataComponents.PROFILE,
                    new ResolvableProfile(new GameProfile(uuid, data.getName(uuid)))));
            named(head, Component.literal(display).withStyle(ChatFormatting.GOLD),
                    line("Hibou n°" + LetterContent.formatNumber(number), ChatFormatting.YELLOW),
                    line(online ? "Présent en ces terres" : "Absent du royaume", online ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                    line("Sélectionner pour lui écrire", ChatFormatting.DARK_GRAY));
            box.setItem(i, head);
            actions.put(i, p -> contactHelp(p, number, display));
        }

        // Séparateur
        for (int i = 36; i < 45; i++) {
            box.setItem(i, named(new ItemStack(Items.BLACK_STAINED_GLASS_PANE), Component.literal(" ")));
        }

        // Boutons
        if (current > 0) {
            box.setItem(45, named(new ItemStack(Items.ARROW), Component.literal("◀ Page précédente").withStyle(ChatFormatting.YELLOW)));
            actions.put(45, p -> openOwleryPage(p, current - 1));
        }
        box.setItem(46, named(new ItemStack(Items.NAME_TAG),
                Component.literal("Mon sceau : n°" + LetterContent.formatNumber(myNumber)).withStyle(ChatFormatting.GOLD),
                line("Votre numéro de hibou est secret.", ChatFormatting.GRAY),
                line("Donnez-le avec /hibou sceau <joueur>", ChatFormatting.DARK_GRAY)));
        actions.put(46, ConsoleUi::sealHelp);

        box.setItem(47, named(new ItemStack(Items.WRITABLE_BOOK), Component.literal("Écrire une lettre").withStyle(ChatFormatting.GOLD),
                line("Écrivez dans un livre et plume,", ChatFormatting.GRAY),
                line("puis /hibou envoyer <n° ou contact>", ChatFormatting.DARK_GRAY)));
        actions.put(47, ConsoleUi::letterHelp);

        box.setItem(48, named(new ItemStack(Items.GUNPOWDER), Component.literal("Envoyer une beuglante").withStyle(ChatFormatting.RED),
                line("Une lettre qui hurle à la face", ChatFormatting.GRAY),
                line("de son destinataire !", ChatFormatting.GRAY),
                line("/hibou beugler <n° ou contact> <cri>", ChatFormatting.DARK_GRAY)));
        actions.put(48, ConsoleUi::beuglanteHelp);

        box.setItem(49, named(new ItemStack(Items.CHEST), Component.literal("Envoyer un colis").withStyle(ChatFormatting.GOLD),
                line("5 emplacements, livré par hibou", ChatFormatting.GRAY),
                line("/hibou colis <n° ou contact>", ChatFormatting.DARK_GRAY)));
        actions.put(49, ConsoleUi::parcelHelp);

        box.setItem(50, named(new ItemStack(Items.PAPER), Component.literal("Ajouter un contact").withStyle(ChatFormatting.GOLD),
                line("/hibou ajouter <numéro> [surnom]", ChatFormatting.DARK_GRAY)));
        actions.put(50, ConsoleUi::addHelp);

        box.setItem(52, named(new ItemStack(Items.BARRIER), Component.literal("Fermer").withStyle(ChatFormatting.RED)));
        actions.put(52, ServerPlayer::closeContainer);

        if (current < pages - 1) {
            box.setItem(53, named(new ItemStack(Items.ARROW), Component.literal("Page suivante ▶").withStyle(ChatFormatting.YELLOW)));
            actions.put(53, p -> openOwleryPage(p, current + 1));
        }

        if (numbers.isEmpty()) {
            box.setItem(13, named(new ItemStack(Items.FEATHER),
                    Component.literal("Votre carnet est vide").withStyle(ChatFormatting.GRAY),
                    line("Demandez son numéro à un joueur,", ChatFormatting.DARK_GRAY),
                    line("puis /hibou ajouter <numéro>", ChatFormatting.DARK_GRAY)));
        }

        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ButtonMenu(id, inv, box, actions),
                Component.literal("Volière — carnet " + (current + 1) + "/" + pages)));
    }

    private static void contactHelp(ServerPlayer p, int number, String name) {
        p.closeContainer();
        p.sendSystemMessage(Msg.prefix().append(Msg.text("Écrire à ")).append(Msg.name(name))
                .append(Msg.text(" (")).append(Msg.number(number)).append(Msg.text(") :")));
        p.sendSystemMessage(Msg.text(" Lettre : écrivez dans un livre et plume, tenez-le en main, puis ")
                .append(Msg.command("/hibou envoyer " + number)));
        p.sendSystemMessage(Msg.text(" Colis : ").append(Msg.command("/hibou colis " + number)));
        p.sendSystemMessage(Msg.text(" Beuglante : ").append(Msg.command("/hibou beugler " + number + " <votre cri>")));
        p.sendSystemMessage(Msg.text(" Rayer du carnet : ").append(Msg.command("/hibou retirer " + number)));
    }

    private static void sealHelp(ServerPlayer p) {
        p.closeContainer();
        int n = OwlPostData.get(p.server).getNumber(p.getUUID());
        p.sendSystemMessage(Msg.prefix().append(Msg.text("Votre hibou porte le ")).append(Msg.number(n))
                .append(Msg.text(". Pour le donner à un joueur proche : ")).append(Msg.command("/hibou sceau <joueur>")));
    }

    private static void letterHelp(ServerPlayer p) {
        p.closeContainer();
        p.sendSystemMessage(Msg.prefix().append(Msg.text("Pour écrire une lettre :")));
        p.sendSystemMessage(Msg.text(" 1. Prenez un livre et plume et écrivez votre missive."));
        p.sendSystemMessage(Msg.text(" 2. Tenez-le en main et tapez ").append(Msg.command("/hibou envoyer <n° ou contact> [objet]")));
        p.sendSystemMessage(Msg.italic(" Le livre est confié au hibou. Les lettres reçues arrivent sous forme de livre."));
    }

    private static void beuglanteHelp(ServerPlayer p) {
        p.closeContainer();
        p.sendSystemMessage(Msg.prefix().append(Msg.text("Pour envoyer une beuglante :")));
        p.sendSystemMessage(Msg.text(" Il vous faut 1 papier et 1 poudre à canon (ou une beuglante vierge)."));
        p.sendSystemMessage(Msg.text(" Tapez ").append(Msg.command("/hibou beugler <n° ou contact> <votre cri>")));
        p.sendSystemMessage(Msg.italic(" Elle éclatera aux oreilles du destinataire et de tous ceux qui l'entourent !"));
    }

    private static void parcelHelp(ServerPlayer p) {
        p.closeContainer();
        p.sendSystemMessage(Msg.prefix().append(Msg.text("Pour envoyer un colis :")));
        p.sendSystemMessage(Msg.text(" Il vous faut un carton ou un coffre dans votre inventaire."));
        p.sendSystemMessage(Msg.text(" Tapez ").append(Msg.command("/hibou colis <n° ou contact>"))
                .append(Msg.text(", déposez jusqu'à 5 objets puis choisissez « Envoyer »")));
    }

    private static void addHelp(ServerPlayer p) {
        p.closeContainer();
        p.sendSystemMessage(Msg.prefix().append(Msg.text("Les numéros de hibou sont secrets : demandez-le au joueur. Puis tapez "))
                .append(Msg.command("/hibou ajouter <numéro> [surnom]")));
    }

    // ==================================================================
    // Colis console : coffre d'une rangée
    // ==================================================================

    public static void openParcel(ServerPlayer player, String recipient) {
        SimpleContainer box = new SimpleContainer(9);
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ConsoleParcelMenu(id, inv, box, recipient),
                Component.literal("Colis pour " + recipient)));
    }

    // ==================================================================
    // Boîte aux lettres console : coffre de 3 rangées, retrait seul
    // ==================================================================

    public static void openMailbox(ServerPlayer player, OwlPostData data, Level level, BlockPos pos) {
        NonNullList<ItemStack> items = data.mailbox(player.getUUID());

        // Adaptation du courrier : lettres -> livres, cartons -> contenu rangé directement
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (s.is(ModItems.SEALED_LETTER.get())) {
                LetterContent c = s.get(ModComponents.LETTER.get());
                items.set(i, c != null ? letterAsBook(c) : ItemStack.EMPTY);
            } else if (s.is(ModItems.SEALED_PARCEL.get())) {
                LetterContent label = s.get(ModComponents.PARCEL_LABEL.get());
                List<ItemStack> contents = OwlPostService.parcelContents(s);
                items.set(i, ItemStack.EMPTY);
                OwlPostService.giveParcelContents(player, contents, label);
            }
        }
        data.markDirty();

        MailContainer container = new MailContainer(data, items);
        ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ConsoleMailboxMenu(id, inv, container, access),
                Component.literal("Boîte aux lettres")));
    }

    // ==================================================================
    // Lettre -> livre écrit vanilla (lisible sur Xbox)
    // ==================================================================

    public static ItemStack letterAsBook(LetterContent c) {
        String date = DATE.format(Instant.ofEpochMilli(c.sentAt()).atZone(ZoneId.systemDefault()));
        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(Filterable.passThrough(Component.literal(
                "Missive de " + c.senderName() + "\nhibou n°" + LetterContent.formatNumber(c.senderNumber())
                        + "\n\nÀ l'attention de\n" + c.recipientName()
                        + "\n\nRédigée le " + date
                        + (c.title().isBlank() ? "" : "\n\nObjet :\n" + c.title()))));
        for (String page : paginate(c.body(), 220)) {
            pages.add(Filterable.passThrough(Component.literal(page)));
        }
        String title = "Missive de " + c.senderName();
        if (title.length() > WrittenBookContent.TITLE_MAX_LENGTH) {
            title = title.substring(0, WrittenBookContent.TITLE_MAX_LENGTH);
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(title), c.senderName(), 0, pages, true));
        return book;
    }

    /** Découpe un texte en pages, sans couper les mots. */
    private static List<String> paginate(String text, int max) {
        List<String> pages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("(?<=\\s)")) {
            if (current.length() + word.length() > max && current.length() > 0) {
                pages.add(current.toString().strip());
                current.setLength(0);
            }
            while (word.length() > max) {
                pages.add(word.substring(0, max));
                word = word.substring(max);
            }
            current.append(word);
        }
        if (current.length() > 0) pages.add(current.toString().strip());
        if (pages.isEmpty()) pages.add("");
        return pages;
    }

    // ==================================================================
    // Utilitaires
    // ==================================================================

    private static ItemStack named(ItemStack stack, Component name, Component... lore) {
        stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(s -> s.withItalic(false)));
        if (lore.length > 0) {
            stack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
        }
        return stack;
    }

    private static MutableComponent line(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(s -> s.withColor(color).withItalic(false));
    }

    /** Resynchronise le menu côté client au tick suivant (le client Bedrock a pu "prédire" un déplacement). */
    private static void resync(ChestMenu menu, Player player) {
        menu.sendAllDataToRemote();
        if (player instanceof ServerPlayer sp) {
            sp.server.tell(new TickTask(sp.server.getTickCount(), () -> {
                if (sp.containerMenu == menu) menu.sendAllDataToRemote();
            }));
        }
    }

    // ==================================================================
    // Menus
    // ==================================================================

    /** Coffre "à boutons" : aucun objet ne peut être pris ni déposé, chaque case peut déclencher une action. */
    static class ButtonMenu extends ChestMenu {
        private final Map<Integer, Consumer<ServerPlayer>> actions;

        ButtonMenu(int id, Inventory inv, Container box, Map<Integer, Consumer<ServerPlayer>> actions) {
            super(MenuType.GENERIC_9x6, id, inv, box, 6);
            this.actions = actions;
        }

        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
            resync(this, player);
            if (!(player instanceof ServerPlayer sp)) return;
            Consumer<ServerPlayer> action = actions.get(slotId);
            if (action != null && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
                // exécuté au tick suivant, hors du traitement du clic
                sp.server.tell(new TickTask(sp.server.getTickCount(), () -> {
                    if (sp.containerMenu == this) action.accept(sp);
                }));
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }

    /** Colis console : cases 0 à 4 pour les objets, case 8 = bouton « Envoyer ». */
    static class ConsoleParcelMenu extends ChestMenu implements OwlPostService.ParcelSource {
        private static final int SEND_SLOT = 8;
        private final Container box;
        private final String recipient;

        ConsoleParcelMenu(int id, Inventory inv, Container box, String recipient) {
            super(MenuType.GENERIC_9x1, id, inv, box, 1);
            this.box = box;
            this.recipient = recipient;
            for (int i = ParcelMenu.SLOTS; i < SEND_SLOT; i++) {
                box.setItem(i, named(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), Component.literal(" ")));
            }
            box.setItem(7, named(new ItemStack(Items.PAPER), Component.literal("Destinataire : " + recipient).withStyle(ChatFormatting.GOLD),
                    line("Déposez jusqu'à 5 objets à gauche.", ChatFormatting.GRAY),
                    line("Il vous faut un carton ou un coffre.", ChatFormatting.GRAY)));
            box.setItem(SEND_SLOT, named(new ItemStack(Items.LIME_DYE), Component.literal("Envoyer le colis").withStyle(ChatFormatting.GREEN)));
        }

        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
            boolean decoration = slotId >= ParcelMenu.SLOTS && slotId <= SEND_SLOT;
            if (decoration || clickType == ClickType.QUICK_CRAFT || clickType == ClickType.PICKUP_ALL
                    || clickType == ClickType.CLONE) {
                resync(this, player);
                if (slotId == SEND_SLOT && clickType == ClickType.PICKUP && player instanceof ServerPlayer sp) {
                    sp.server.tell(new TickTask(sp.server.getTickCount(), () -> {
                        if (sp.containerMenu == this) OwlPostService.sendParcel(sp, recipient, "", this);
                    }));
                }
                return;
            }
            super.clicked(slotId, button, clickType, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index >= ParcelMenu.SLOTS && index < 9) return ItemStack.EMPTY;
            var slot = slots.get(index);
            if (!slot.hasItem()) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            ItemStack result = stack.copy();
            if (index < ParcelMenu.SLOTS) {
                if (!moveItemStackTo(stack, 9, slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (stack.is(ModItems.SEALED_PARCEL.get()) || !moveItemStackTo(stack, 0, ParcelMenu.SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            return result;
        }

        @Override
        public boolean isParcelEmpty() {
            for (int i = 0; i < ParcelMenu.SLOTS; i++) {
                if (!box.getItem(i).isEmpty()) return false;
            }
            return true;
        }

        @Override
        public List<ItemStack> takeAll() {
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < ParcelMenu.SLOTS; i++) {
                items.add(box.getItem(i).copy());
                box.setItem(i, ItemStack.EMPTY);
            }
            return items;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            // on rend les objets déposés (jamais les cases décoratives)
            SimpleContainer back = new SimpleContainer(ParcelMenu.SLOTS);
            for (int i = 0; i < ParcelMenu.SLOTS; i++) {
                back.setItem(i, box.getItem(i));
            }
            box.clearContent();
            if (!player.level().isClientSide()) {
                clearContainer(player, back);
            }
        }
    }

    /** Boîte aux lettres console : on peut prendre son courrier, jamais en déposer. */
    static class ConsoleMailboxMenu extends ChestMenu {
        private static final int SIZE = OwlPostData.MAILBOX_SIZE;
        private final ContainerLevelAccess access;

        ConsoleMailboxMenu(int id, Inventory inv, Container mail, ContainerLevelAccess access) {
            super(MenuType.GENERIC_9x3, id, inv, mail, 3);
            this.access = access;
        }

        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
            boolean inBox = slotId >= 0 && slotId < SIZE;
            boolean deny = switch (clickType) {
                case PICKUP -> inBox && !getCarried().isEmpty();
                case QUICK_MOVE -> !inBox;
                case SWAP, QUICK_CRAFT, CLONE -> true;
                default -> false;
            };
            if (deny) {
                resync(this, player);
                return;
            }
            super.clicked(slotId, button, clickType, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index >= SIZE) return ItemStack.EMPTY;
            return super.quickMoveStack(player, index);
        }

        @Override
        public boolean stillValid(Player player) {
            return stillValid(access, player, ModBlocks.MAILBOX.get());
        }
    }
}
