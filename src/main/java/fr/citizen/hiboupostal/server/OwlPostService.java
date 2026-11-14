package fr.citizen.hiboupostal.server;

import fr.citizen.hiboupostal.Config;
import fr.citizen.hiboupostal.data.OwlPostData;
import fr.citizen.hiboupostal.item.LetterContent;
import fr.citizen.hiboupostal.item.ModComponents;
import fr.citizen.hiboupostal.item.ModItems;
import fr.citizen.hiboupostal.menu.MailContainer;
import fr.citizen.hiboupostal.menu.MailboxMenu;
import fr.citizen.hiboupostal.menu.ParcelMenu;
import fr.citizen.hiboupostal.network.Payloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Logique serveur : numéros de hibou, carnet de contacts, envoi et remise
 * des lettres, cartons et beuglantes, boîtes aux lettres, et mode console (Xbox).
 */
public final class OwlPostService {
    private OwlPostService() {}

    private static final Map<UUID, Long> LAST_SEND = new HashMap<>();
    /** Joueurs PC forcés en mode console (pour tester l'interface Xbox). */
    private static final Set<UUID> FORCED_CONSOLE = new HashSet<>();

    /** Nature d'un envoi. */
    public enum Kind { LETTER, PARCEL, BEUGLANTE }

    /** Où le hibou a finalement déposé l'envoi. */
    private enum Destination { INVENTORY, MAILBOX, GROUND, BURST }

    // ==================================================================
    // Détection des joueurs console (Xbox / Bedrock via Geyser)
    // ==================================================================

    /**
     * Un joueur "console" est un joueur dont le client n'a pas le mod Hibou Postal
     * (joueur Bedrock passé par Geyser, ou client vanilla). Il n'a donc ni nos écrans ni nos objets :
     * on lui présente des menus de coffre vanilla, des livres vanilla et des commandes.
     */
    public static boolean isConsole(ServerPlayer player) {
        return FORCED_CONSOLE.contains(player.getUUID())
                || !player.connection.hasChannel(Payloads.OpenOwlery.TYPE);
    }

    /** Bascule le mode console forcé ; renvoie le nouvel état. */
    public static boolean toggleForcedConsole(ServerPlayer player) {
        if (!FORCED_CONSOLE.remove(player.getUUID())) {
            FORCED_CONSOLE.add(player.getUUID());
            return true;
        }
        return false;
    }

    /** N'envoie un paquet du mod qu'aux joueurs qui ont le mod (sinon NeoForge refuse). */
    public static void sendToModded(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    // ==================================================================
    // Connexion
    // ==================================================================

    public static void onLogin(ServerPlayer player) {
        OwlPostData data = OwlPostData.get(player.server);
        boolean isNew = !data.hasNumber(player.getUUID());
        int number = data.getOrAssign(player.getUUID(), player.getGameProfile().getName());

        if (isNew) {
            player.sendSystemMessage(Msg.prefix()
                    .append(Msg.text("Bienvenue en ces terres, voyageur. Un jeune hibou vous a été confié : il portera le "))
                    .append(Msg.number(number))
                    .append(Msg.text(". Ce numéro est secret : ne le donnez qu'à ceux qui doivent vous écrire ("))
                    .append(Msg.command("/hibou sceau <joueur>"))
                    .append(Msg.text("). Tapez "))
                    .append(Msg.command("/hibou"))
                    .append(Msg.text(" pour ouvrir la volière.")));
        }

        mailboxNotice(player, data);

        int waiting = data.countPendingFor(player.getUUID());
        if (waiting > 0) {
            player.sendSystemMessage(Msg.prefix()
                    .append(Msg.text(waiting == 1
                            ? "Un hibou tournoie au-dessus de vous, un envoi entre ses serres..."
                            : waiting + " hiboux tournoient au-dessus de vous, chacun portant un envoi...")));
        }
    }

    // ==================================================================
    // Carnet de contacts
    // ==================================================================

    public static List<Payloads.DirectoryEntry> contactEntries(ServerPlayer player) {
        OwlPostData data = OwlPostData.get(player.server);
        List<Payloads.DirectoryEntry> list = new ArrayList<>();
        for (Integer number : data.contacts(player.getUUID()).keySet()) {
            Optional<UUID> id = data.findByNumber(number);
            boolean online = id.isPresent() && player.server.getPlayerList().getPlayer(id.get()) != null;
            list.add(new Payloads.DirectoryEntry(number, data.contactDisplayName(player.getUUID(), number), online));
        }
        return list;
    }

    /** Ajoute un contact par son numéro (on ne peut ajouter que quelqu'un dont on connaît le numéro). */
    public static boolean addContact(ServerPlayer player, int number, String nickname) {
        OwlPostData data = OwlPostData.get(player.server);
        Optional<UUID> target = data.findByNumber(number);
        if (target.isEmpty()) {
            player.sendSystemMessage(Msg.error("Nul hibou ne porte le n°" + LetterContent.formatNumber(number) + "."));
            return false;
        }
        if (target.get().equals(player.getUUID())) {
            player.sendSystemMessage(Msg.error("C'est votre propre hibou !"));
            return false;
        }
        Map<Integer, String> book = data.contacts(player.getUUID());
        if (!book.containsKey(number) && book.size() >= Config.MAX_CONTACTS.get()) {
            player.sendSystemMessage(Msg.error("Votre carnet est plein (" + Config.MAX_CONTACTS.get() + " contacts)."));
            return false;
        }
        String nick = sanitize(nickname, 24, false);
        data.addContact(player.getUUID(), number, nick);
        player.sendSystemMessage(Msg.prefix()
                .append(Msg.text("Vous inscrivez dans votre carnet le hibou "))
                .append(Msg.number(number))
                .append(Msg.text(" : "))
                .append(Msg.name(data.contactDisplayName(player.getUUID(), number)))
                .append(Msg.text(".")));
        refreshContacts(player, "Contact ajouté.");
        return true;
    }

    public static boolean removeContact(ServerPlayer player, int number) {
        OwlPostData data = OwlPostData.get(player.server);
        String name = data.contactDisplayName(player.getUUID(), number);
        if (!data.removeContact(player.getUUID(), number)) {
            player.sendSystemMessage(Msg.error("Ce hibou n'est pas dans votre carnet."));
            return false;
        }
        player.sendSystemMessage(Msg.prefix().append(Msg.text("Vous rayez "))
                .append(Msg.name(name)).append(Msg.text(" de votre carnet.")));
        refreshContacts(player, "Contact retiré.");
        return true;
    }

    private static void refreshContacts(ServerPlayer player, String message) {
        sendToModded(player, new Payloads.ContactsUpdate(contactEntries(player), message));
    }

    /** /hibou sceau : tend son numéro à un joueur proche. */
    public static void giveSeal(ServerPlayer player, ServerPlayer target) {
        if (target == player) {
            player.sendSystemMessage(Msg.error("Vous admirez votre propre sceau... sans grand intérêt."));
            return;
        }
        int range = Config.SEAL_RANGE.get();
        if (target.level() != player.level() || target.distanceToSqr(player) > (double) range * range) {
            player.sendSystemMessage(Msg.error(target.getGameProfile().getName() + " est trop loin. Approchez-vous (" + range + " blocs)."));
            return;
        }
        OwlPostData data = OwlPostData.get(player.server);
        int number = data.getOrAssign(player.getUUID(), player.getGameProfile().getName());
        String name = player.getGameProfile().getName();

        player.sendSystemMessage(Msg.prefix().append(Msg.text("Vous tendez votre sceau à "))
                .append(Msg.name(target.getGameProfile().getName())).append(Msg.text(".")));

        var msg = Msg.prefix()
                .append(Msg.name(name))
                .append(Msg.text(" vous tend son sceau : il porte le "))
                .append(Msg.number(number))
                .append(Msg.text(". "));
        if (isConsole(target)) {
            msg.append(Msg.text("Pour l'inscrire dans votre carnet, tapez "))
                    .append(Msg.command("/hibou ajouter " + number));
        } else {
            msg.append(Msg.button("Ajouter au carnet", "/hibou ajouter " + number,
                    "Inscrire " + name + " dans votre carnet de contacts"));
        }
        target.sendSystemMessage(msg);
    }

    // ==================================================================
    // Volière
    // ==================================================================

    public static void openOwlery(ServerPlayer player, String prefill) {
        OwlPostData data = OwlPostData.get(player.server);
        int myNumber = data.getOrAssign(player.getUUID(), player.getGameProfile().getName());

        if (isConsole(player)) {
            ConsoleUi.openOwlery(player);
            return;
        }

        PacketDistributor.sendToPlayer(player, new Payloads.OpenOwlery(
                myNumber,
                count(player, ModItems.BLANK_LETTER.get()),
                beuglantesAvailable(player),
                Config.MAX_TITLE_LENGTH.get(),
                Config.MAX_BODY_LENGTH.get(),
                Config.BEUGLANTE_MAX_SECONDS.get(),
                safePrefill(prefill),
                contactEntries(player)));
    }

    // ==================================================================
    // Comptoir d'expédition (cartons)
    // ==================================================================

    public static void openParcel(ServerPlayer player, String prefill) {
        OwlPostData.get(player.server).getOrAssign(player.getUUID(), player.getGameProfile().getName());
        String pre = safePrefill(prefill);

        if (isConsole(player)) {
            if (pre.isEmpty()) {
                player.sendSystemMessage(Msg.error("Indiquez le destinataire : /hibou colis <numéro ou contact>"));
                return;
            }
            ConsoleUi.openParcel(player, pre);
            return;
        }

        int cartons = count(player, ModItems.PARCEL.get());
        player.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new ParcelMenu(id, inv, pre, cartons),
                        Component.literal("Expédition d'un carton")),
                buf -> {
                    buf.writeUtf(pre, 64);
                    buf.writeVarInt(cartons);
                });
    }

    // ==================================================================
    // Envoi d'une lettre
    // ==================================================================

    /** Depuis la volière PC : consomme une lettre vierge. */
    public static void handleSend(ServerPlayer sender, Payloads.SendLetter payload) {
        sendLetter(sender, payload.recipient(), payload.title(), payload.body(), null);
    }

    /**
     * Depuis /hibou envoyer : le livre et plume (ou livre signé) tenu en main devient la lettre.
     * Fonctionne sur toutes les plateformes, notamment Xbox.
     */
    public static void sendBookInHand(ServerPlayer sender, String recipient, String subject) {
        ItemStack book = sender.getMainHandItem();
        String body;
        String title = subject == null ? "" : subject;
        if (book.is(Items.WRITABLE_BOOK)) {
            WritableBookContent content = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
            List<String> pages = new ArrayList<>();
            if (content != null) content.pages().forEach(f -> pages.add(f.raw()));
            body = String.join("\n", pages);
        } else if (book.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
            List<String> pages = new ArrayList<>();
            if (content != null) {
                content.pages().forEach(f -> pages.add(f.raw().getString()));
                if (title.isBlank()) title = content.title().raw();
            }
            body = String.join("\n", pages);
        } else {
            sender.sendSystemMessage(Msg.error("Tenez en main un livre et plume contenant votre missive, puis tapez /hibou envoyer <numéro ou contact>."));
            return;
        }
        sendLetter(sender, recipient, title, body, book);
    }

    /**
     * @param book si non nul, le livre à consommer à la place d'une lettre vierge.
     */
    private static void sendLetter(ServerPlayer sender, String recipient, String rawTitle, String rawBody, ItemStack book) {
        OwlPostData data = OwlPostData.get(sender.server);
        if (!checkCooldown(sender, Kind.LETTER)) return;

        String title = sanitize(rawTitle, Config.MAX_TITLE_LENGTH.get(), false);
        String body = sanitize(rawBody, Config.MAX_BODY_LENGTH.get(), true);
        if (body.isBlank()) {
            fail(sender, "Une missive vide ? Votre hibou refuse de s'envoler pour si peu.", Kind.LETTER);
            return;
        }

        UUID recipientId = resolveOrFail(sender, data, recipient, Kind.LETTER);
        if (recipientId == null) return;

        if (book != null) {
            if (book.isEmpty() || sender.getMainHandItem() != book) {
                fail(sender, "Le livre a quitté votre main.", Kind.LETTER);
                return;
            }
            book.shrink(1);
        } else if (!consumeOne(sender, ModItems.BLANK_LETTER.get())) {
            fail(sender, "Vous n'avez point de lettre vierge ! Fabriquez-en avec du papier et une plume, "
                    + "ou écrivez dans un livre et plume puis tapez /hibou envoyer.", Kind.LETTER);
            return;
        }

        LetterContent content = label(sender, data, recipientId, title, body);
        ItemStack letter = new ItemStack(ModItems.SEALED_LETTER.get());
        letter.set(ModComponents.LETTER.get(), content);

        dispatch(sender, data, recipientId, letter, Kind.LETTER, content);
        sender.sendSystemMessage(Msg.prefix()
                .append(Msg.text("Vous nouez la missive à la patte de votre hibou. Il s'élève dans le ciel et file vers "))
                .append(Msg.name(data.contactDisplayName(sender.getUUID(), content.recipientNumber())))
                .append(Msg.text(" ("))
                .append(Msg.number(content.recipientNumber()))
                .append(Msg.text(").")));
        absentNotice(sender, recipientId, Kind.LETTER);

        sendToModded(sender, new Payloads.SendResult(true, "Votre hibou s'est envolé !",
                count(sender, ModItems.BLANK_LETTER.get())));
    }

    // ==================================================================
    // Envoi d'un carton
    // ==================================================================

    /** Source des objets d'un carton (menu PC ou menu de coffre console). */
    public interface ParcelSource {
        boolean isParcelEmpty();

        List<ItemStack> takeAll();
    }

    public static void handleSendParcel(ServerPlayer sender, Payloads.SendParcel payload) {
        if (!(sender.containerMenu instanceof ParcelMenu menu)) {
            return; // le joueur n'a pas le comptoir d'expédition ouvert
        }
        sendParcel(sender, payload.recipient(), payload.note(), menu);
    }

    /** @return true si le carton est parti (le menu doit alors être fermé par l'appelant ou ici). */
    public static boolean sendParcel(ServerPlayer sender, String recipient, String rawNote, ParcelSource source) {
        OwlPostData data = OwlPostData.get(sender.server);
        if (!checkCooldown(sender, Kind.PARCEL)) return false;

        if (source.isParcelEmpty()) {
            fail(sender, "Votre carton est vide. Déposez-y au moins un objet.", Kind.PARCEL);
            return false;
        }

        UUID recipientId = resolveOrFail(sender, data, recipient, Kind.PARCEL);
        if (recipientId == null) return false;

        boolean paid = consumeOne(sender, ModItems.PARCEL.get())
                || (isConsole(sender) && consumeOne(sender, Items.CHEST));
        if (!paid) {
            fail(sender, isConsole(sender)
                    ? "Il vous faut un carton ou un coffre dans votre inventaire pour emballer l'envoi."
                    : "Il vous faut un carton vide dans votre inventaire (4 papiers + 1 ficelle).", Kind.PARCEL);
            return false;
        }

        String note = sanitize(rawNote, ParcelMenu.MAX_NOTE_LENGTH, false);
        LetterContent content = label(sender, data, recipientId, note, "");

        // On retire les objets du menu AVANT de fermer, pour qu'ils ne soient pas rendus au joueur.
        List<ItemStack> items = source.takeAll();
        ItemStack parcel = new ItemStack(ModItems.SEALED_PARCEL.get());
        parcel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        parcel.set(ModComponents.PARCEL_LABEL.get(), content);

        sender.closeContainer();

        dispatch(sender, data, recipientId, parcel, Kind.PARCEL, content);
        sender.sendSystemMessage(Msg.prefix()
                .append(Msg.text("Un robuste hibou grand-duc empoigne votre carton ficelé et l'emporte vers "))
                .append(Msg.name(data.contactDisplayName(sender.getUUID(), content.recipientNumber())))
                .append(Msg.text(" ("))
                .append(Msg.number(content.recipientNumber()))
                .append(Msg.text(")."))
                .append(Msg.italic(" (" + describe(items) + ")")));
        absentNotice(sender, recipientId, Kind.PARCEL);
        return true;
    }

    // ==================================================================
    // Envoi d'une beuglante
    // ==================================================================

    /** Nombre de beuglantes que le joueur peut envoyer (beuglantes vierges, ou papier + poudre à canon). */
    public static int beuglantesAvailable(ServerPlayer player) {
        return count(player, ModItems.BLANK_BEUGLANTE.get())
                + Math.min(count(player, Items.PAPER), count(player, Items.GUNPOWDER));
    }

    /** Depuis la volière PC (cri écrit + enregistrement vocal facultatif). */
    public static void handleSendBeuglante(ServerPlayer sender, Payloads.SendBeuglante payload) {
        byte[] audio = payload.uploadId() >= 0 ? Beuglantes.takeUpload(sender, payload.uploadId()) : null;
        if (payload.uploadId() >= 0 && audio == null) {
            fail(sender, "L'enregistrement vocal n'est pas arrivé entier. Réessayez.", Kind.BEUGLANTE);
            return;
        }
        sendBeuglante(sender, payload.recipient(), payload.cry(), audio);
    }

    /** Depuis /hibou beugler (toutes plateformes, cri écrit seulement). */
    public static void sendBeuglante(ServerPlayer sender, String recipient, String rawCry, byte[] audio) {
        OwlPostData data = OwlPostData.get(sender.server);
        if (!checkCooldown(sender, Kind.BEUGLANTE)) return;

        String cry = sanitize(rawCry, Beuglantes.MAX_CRY_LENGTH, false);
        if (cry.isBlank()) {
            fail(sender, "Une beuglante doit hurler quelque chose ! Écrivez votre cri.", Kind.BEUGLANTE);
            return;
        }

        UUID recipientId = resolveOrFail(sender, data, recipient, Kind.BEUGLANTE);
        if (recipientId == null) return;

        boolean paid = consumeOne(sender, ModItems.BLANK_BEUGLANTE.get());
        if (!paid && count(sender, Items.PAPER) > 0 && count(sender, Items.GUNPOWDER) > 0) {
            paid = consumeOne(sender, Items.PAPER) && consumeOne(sender, Items.GUNPOWDER);
        }
        if (!paid) {
            fail(sender, "Il vous faut une beuglante vierge, ou bien 1 papier et 1 poudre à canon.", Kind.BEUGLANTE);
            return;
        }

        String audioId = "";
        if (audio != null && audio.length > 0) {
            audioId = Beuglantes.store(sender.server, audio);
        }

        // Pour une beuglante : title = cri, body = identifiant de l'enregistrement vocal ("" = aucun)
        LetterContent content = label(sender, data, recipientId, cry, audioId);
        ItemStack beuglante = new ItemStack(ModItems.BEUGLANTE.get());
        beuglante.set(ModComponents.BEUGLANTE.get(), content);

        dispatch(sender, data, recipientId, beuglante, Kind.BEUGLANTE, content);
        sender.sendSystemMessage(Msg.prefix()
                .append(Msg.text("La beuglante fume déjà entre les serres de votre hibou ! Il file à tire-d'aile vers "))
                .append(Msg.name(data.contactDisplayName(sender.getUUID(), content.recipientNumber())))
                .append(Msg.text(" ("))
                .append(Msg.number(content.recipientNumber()))
                .append(Msg.text(")."))
                .append(Msg.italic(audioId.isEmpty() ? "" : " (avec votre voix)")));
        absentNotice(sender, recipientId, Kind.BEUGLANTE);

        sendToModded(sender, new Payloads.SendResult(true, "Votre beuglante est partie !", beuglantesAvailable(sender)));
    }

    // ==================================================================
    // Ouverture d'un carton reçu
    // ==================================================================

    /**
     * Ouverture d'un carton tenu en main.
     *
     * IMPORTANT : on ne vide PAS le carton pendant Item#use(). Après use(), Minecraft
     * (ServerPlayerGameMode#useItem) remet de force la main à "vide" si la pile tenue a
     * diminué : un objet du carton rangé dans l'emplacement de la main était alors écrasé
     * et perdu. On reporte donc l'ouverture d'un tick, hors de ce traitement.
     */
    public static void scheduleUnpack(ServerPlayer player, InteractionHand hand, ItemStack sealedParcel) {
        MinecraftServer server = player.server;
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (player.isRemoved()) return;
            ItemStack inHand = player.getItemInHand(hand);
            // le joueur a pu changer d'objet entre-temps : on n'ouvre que le même carton
            if (inHand != sealedParcel || !inHand.is(ModItems.SEALED_PARCEL.get())) return;
            List<ItemStack> items = parcelContents(inHand);
            LetterContent label = inHand.get(ModComponents.PARCEL_LABEL.get());
            // le carton est déchiré à l'ouverture : on libère la main AVANT de ranger le contenu
            inHand.shrink(1);
            if (inHand.isEmpty()) player.setItemInHand(hand, ItemStack.EMPTY);
            giveParcelContents(player, items, label);
        }));
    }

    public static List<ItemStack> parcelContents(ItemStack sealedParcel) {
        ItemContainerContents contents = sealedParcel.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        NonNullList<ItemStack> slots = NonNullList.withSize(ParcelMenu.SLOTS, ItemStack.EMPTY);
        contents.copyInto(slots);
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack s : slots) {
            if (!s.isEmpty()) items.add(s.copy());
        }
        return items;
    }

    /** Range le contenu d'un carton dans l'inventaire (le surplus tombe aux pieds du joueur). */
    public static void giveParcelContents(ServerPlayer player, List<ItemStack> items, LetterContent label) {
        boolean dropped = false;
        for (ItemStack stack : items) {
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
                dropped = true;
            }
        }
        player.inventoryMenu.broadcastChanges();

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1.0F, 0.9F);

        var msg = Msg.prefix().append(Msg.text("Vous tranchez la ficelle et déchirez le carton"));
        if (label != null) {
            msg.append(Msg.text(" de ")).append(Msg.name(label.senderName()));
        }
        msg.append(Msg.text(" : ")).append(Msg.italic(items.isEmpty() ? "il est vide..." : describe(items)));
        player.sendSystemMessage(msg);
        if (label != null && !label.title().isBlank()) {
            player.sendSystemMessage(Msg.prefix().append(Msg.text("Un mot est glissé à l'intérieur : "))
                    .append(Msg.flavor("« " + label.title() + " »")));
        }
        if (dropped) {
            player.sendSystemMessage(Msg.prefix().append(Msg.italic("Votre besace déborde : une partie du contenu est tombée à vos pieds.")));
        }
    }

    // ==================================================================
    // Remise (appelé à chaque tick serveur)
    // ==================================================================

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        Beuglantes.cleanupUploads();
        OwlPostData data = OwlPostData.get(server);
        if (data.pending().isEmpty()) return;

        long now = System.currentTimeMillis();
        for (OwlPostData.PendingLetter p : new ArrayList<>(data.pending())) {
            if (p.deliverAt() > now) continue;
            Kind kind = kindOf(p.item());
            ServerPlayer recipient = server.getPlayerList().getPlayer(p.recipient());

            if (recipient == null) {
                // Absent : lettres et cartons vont dans sa boîte aux lettres s'il en a une (et qu'elle n'est pas pleine).
                // Une beuglante, elle, attend son retour pour lui hurler dessus.
                if (kind != Kind.BEUGLANTE && data.hasMailbox(p.recipient())
                        && data.storeInMailbox(p.recipient(), p.item().copy())) {
                    data.removePending(p);
                    notifySender(server, p, kind, Destination.MAILBOX);
                }
                continue;
            }
            if (recipient.tickCount < 100) continue; // on laisse le joueur finir de se connecter (5 s)

            data.removePending(p);
            Destination dest = deliverTo(recipient, data, p, kind);
            notifyRecipient(recipient, p, kind, dest);
            notifySender(server, p, kind, dest);
        }
    }

    /** Remet l'envoi au joueur connecté et renvoie l'endroit où il a été déposé. */
    private static Destination deliverTo(ServerPlayer recipient, OwlPostData data, OwlPostData.PendingLetter p, Kind kind) {
        boolean console = isConsole(recipient);
        ItemStack stack = p.item().copy();

        if (kind == Kind.BEUGLANTE) {
            // Sur console, ou si la besace est pleine : elle éclate aussitôt.
            if (!console) {
                int auto = Config.BEUGLANTE_AUTO_OPEN_SECONDS.get();
                stack.set(ModComponents.BEUGLANTE_DEADLINE.get(),
                        auto > 0 ? System.currentTimeMillis() + auto * 1000L : Long.MAX_VALUE);
                if (recipient.getInventory().add(stack) && stack.isEmpty()) {
                    return Destination.INVENTORY;
                }
            }
            return Destination.BURST;
        }

        if (console) {
            if (kind == Kind.PARCEL) {
                // Les consoles ne voient pas nos objets : le carton est ouvert directement.
                giveParcelContents(recipient, parcelContents(stack), p.label());
                return Destination.INVENTORY;
            }
            stack = ConsoleUi.letterAsBook(p.label());
        }

        if (recipient.getInventory().add(stack) && stack.isEmpty()) {
            return Destination.INVENTORY;
        }
        if (data.hasMailbox(p.recipient()) && data.storeInMailbox(p.recipient(), p.item().copy())) {
            return Destination.MAILBOX;
        }
        recipient.drop(stack, false);
        return Destination.GROUND;
    }

    public static Kind kindOf(ItemStack stack) {
        if (stack.is(ModItems.SEALED_PARCEL.get())) return Kind.PARCEL;
        if (stack.is(ModItems.BEUGLANTE.get())) return Kind.BEUGLANTE;
        return Kind.LETTER;
    }

    private static void notifyRecipient(ServerPlayer recipient, OwlPostData.PendingLetter p, Kind kind, Destination dest) {
        LetterContent c = p.label();
        boolean console = isConsole(recipient);
        OwlPostData data = OwlPostData.get(recipient.server);
        String from = data.hasContact(recipient.getUUID(), c.senderNumber())
                ? data.contactDisplayName(recipient.getUUID(), c.senderNumber())
                : c.senderName();

        recipient.playNotifySound(SoundEvents.PARROT_FLY, SoundSource.PLAYERS, 1.0F, kind == Kind.PARCEL ? 0.6F : 0.8F);
        recipient.serverLevel().sendParticles(ParticleTypes.CLOUD,
                recipient.getX(), recipient.getY() + 2.0, recipient.getZ(), 8, 0.3, 0.2, 0.3, 0.01);

        if (kind == Kind.BEUGLANTE) {
            recipient.sendSystemMessage(Msg.prefix().append(Msg.flavor(
                    "Un hibou affolé fond sur vous, une enveloppe rouge fumante dans le bec !")));
            if (dest == Destination.BURST) {
                Beuglantes.burst(recipient, c);
            } else {
                int auto = Config.BEUGLANTE_AUTO_OPEN_SECONDS.get();
                recipient.sendSystemMessage(Msg.prefix()
                        .append(Msg.text("Une "))
                        .append(Msg.name("beuglante"))
                        .append(Msg.text(" de "))
                        .append(Msg.name(from))
                        .append(Msg.text(" ("))
                        .append(Msg.number(c.senderNumber()))
                        .append(Msg.text(") tremble dans votre besace. "))
                        .append(Msg.italic(auto > 0
                                ? "Ouvrez-la d'un clic droit... sinon elle éclatera d'elle-même dans " + auto + " secondes !"
                                : "Ouvrez-la d'un clic droit... si vous l'osez.")));
            }
            return;
        }

        recipient.sendSystemMessage(Msg.prefix()
                .append(Msg.flavor(kind == Kind.PARCEL
                        ? "Un lourd battement d'ailes fait trembler l'air autour de vous..."
                        : "Un battement d'ailes résonne au-dessus de vous...")));

        var arrival = Msg.prefix();
        if (dest == Destination.MAILBOX) {
            arrival.append(Msg.text(kind == Kind.PARCEL
                    ? "Votre besace est pleine ! Le hibou grand-duc a déposé dans votre boîte aux lettres un carton envoyé par "
                    : "Votre besace est pleine ! Le hibou a glissé dans votre boîte aux lettres une missive de "));
        } else if (kind == Kind.PARCEL) {
            arrival.append(Msg.text("Un hibou grand-duc dépose devant vous un carton ficelé, envoyé par "));
        } else {
            arrival.append(Msg.text("Un hibou se pose sur votre épaule et vous remet une missive scellée de la part de "));
        }
        arrival.append(Msg.name(from))
                .append(Msg.text(" ("))
                .append(Msg.number(c.senderNumber()))
                .append(Msg.text(")."));
        if (kind == Kind.LETTER && !c.title().isBlank()) {
            arrival.append(Msg.text(" Objet : « " + c.title() + " »."));
        }
        recipient.sendSystemMessage(arrival);

        String hint = switch (dest) {
            case MAILBOX -> "Videz votre besace puis allez relever votre boîte aux lettres. ";
            case GROUND -> "Votre besace étant pleine et sans boîte aux lettres libre, le hibou l'a déposé à vos pieds. ";
            default -> kind == Kind.PARCEL
                    ? (console ? "Le contenu a été rangé dans votre besace. " : "Clic droit sur le carton pour l'ouvrir. ")
                    : (console ? "La missive est dans votre besace, sous forme de livre. " : "Brisez le sceau d'un clic droit sur la lettre pour la lire. ");
        };
        var hintMsg = Msg.prefix().append(Msg.italic(hint));
        if (console) {
            hintMsg.append(Msg.text("Pour répondre : ")).append(Msg.command("/hibou envoyer " + c.senderNumber()));
        } else {
            hintMsg.append(Msg.button("Répondre", "/hibou ecrire " + c.senderNumber(),
                    "Écrire à " + from + " (n°" + LetterContent.formatNumber(c.senderNumber()) + ")"));
        }
        recipient.sendSystemMessage(hintMsg);
    }

    private static void notifySender(MinecraftServer server, OwlPostData.PendingLetter p, Kind kind, Destination dest) {
        ServerPlayer sender = server.getPlayerList().getPlayer(p.sender());
        if (sender == null) return;
        OwlPostData data = OwlPostData.get(server);
        String to = data.contactDisplayName(sender.getUUID(), p.label().recipientNumber());
        String what = switch (kind) {
            case PARCEL -> "le carton";
            case BEUGLANTE -> "la beuglante";
            default -> "la missive";
        };
        String text = switch (dest) {
            case INVENTORY -> "Votre hibou est de retour : " + what + " a été remis" + (kind == Kind.PARCEL ? "" : "e") + " à ";
            case MAILBOX -> "Votre hibou est de retour : il a déposé " + what + " dans la boîte aux lettres de ";
            case GROUND -> "Votre hibou est de retour : il a déposé " + what + " aux pieds de ";
            case BURST -> "Votre hibou est de retour : la beuglante a éclaté aux oreilles de ";
        };
        sender.sendSystemMessage(Msg.prefix()
                .append(Msg.text(text))
                .append(Msg.name(to))
                .append(Msg.text(" !")));
    }

    // ==================================================================
    // Boîte aux lettres
    // ==================================================================

    public static void openMailbox(ServerPlayer player, Level level, BlockPos pos) {
        OwlPostData data = OwlPostData.get(player.server);
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.2F);

        if (isConsole(player)) {
            ConsoleUi.openMailbox(player, data, level, pos);
            return;
        }

        MailContainer container = new MailContainer(data, data.mailbox(player.getUUID()));
        String owner = player.getGameProfile().getName();
        player.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new MailboxMenu(id, inv, container, ContainerLevelAccess.create(level, pos), owner),
                        Component.literal("Boîte aux lettres")),
                buf -> buf.writeUtf(owner, 64));
    }

    /** Avertit le joueur, à sa connexion, du courrier qui l'attend dans sa boîte aux lettres. */
    private static void mailboxNotice(ServerPlayer player, OwlPostData data) {
        UUID id = player.getUUID();
        int letters = data.countInMailbox(id, s -> s.is(ModItems.SEALED_LETTER.get()) || s.is(Items.WRITTEN_BOOK));
        int parcels = data.countInMailbox(id, s -> s.is(ModItems.SEALED_PARCEL.get()));
        int total = data.countInMailbox(id, s -> true);
        if (total == 0) return;

        List<String> parts = new ArrayList<>();
        if (letters > 0) parts.add(letters + (letters > 1 ? " missives" : " missive"));
        if (parcels > 0) parts.add(parcels + (parcels > 1 ? " cartons" : " carton"));
        int other = total - letters - parcels;
        if (other > 0) parts.add(other + (other > 1 ? " autres plis" : " autre pli"));

        player.playNotifySound(SoundEvents.PARROT_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.7F);
        player.sendSystemMessage(Msg.prefix()
                .append(Msg.flavor("Durant votre absence, les hiboux n'ont point chômé..."))
                .append(Msg.text(" Votre boîte aux lettres contient "))
                .append(Msg.name(String.join(" et ", parts)))
                .append(Msg.text(".")));
        if (!data.hasMailbox(id)) {
            player.sendSystemMessage(Msg.prefix().append(Msg.italic(
                    "Vous n'avez plus de boîte aux lettres en ces terres : posez-en une pour relever votre courrier.")));
        } else if (total >= OwlPostData.MAILBOX_SIZE) {
            player.sendSystemMessage(Msg.error("Votre boîte aux lettres est pleine ! Les prochains hiboux devront attendre votre retour."));
        }
    }

    // ==================================================================
    // Utilitaires communs
    // ==================================================================

    private static boolean checkCooldown(ServerPlayer sender, Kind kind) {
        long now = System.currentTimeMillis();
        long cooldownMs = Config.SEND_COOLDOWN_SECONDS.get() * 1000L;
        Long last = LAST_SEND.get(sender.getUUID());
        if (last != null && now - last < cooldownMs) {
            long remaining = (cooldownMs - (now - last) + 999) / 1000;
            fail(sender, "Votre hibou reprend son souffle. Patientez encore " + remaining + " s.", kind);
            return false;
        }
        return true;
    }

    /** Trouve le destinataire et vérifie qu'on peut lui écrire ; null en cas d'échec (message déjà envoyé). */
    private static UUID resolveOrFail(ServerPlayer sender, OwlPostData data, String query, Kind kind) {
        Optional<UUID> target = resolveRecipient(data, sender.getUUID(), query);
        if (target.isEmpty()) {
            fail(sender, "Nul hibou ne répond à « " + (query == null ? "" : query.trim())
                    + " ». Indiquez un numéro de hibou ou le nom d'un contact de votre carnet.", kind);
            return null;
        }
        UUID id = target.get();
        if (id.equals(sender.getUUID())) {
            fail(sender, "Votre hibou vous fixe, perplexe... On ne s'envoie point de courrier à soi-même.", kind);
            return null;
        }
        if (sender.server.getPlayerList().getPlayer(id) == null && !Config.ALLOW_OFFLINE_DELIVERY.get()) {
            fail(sender, "Ce sujet n'est point en ces terres. Revenez quand il sera présent.", kind);
            return null;
        }
        return id;
    }

    /**
     * Destinataire : un numéro de hibou ("7", "0007", "#7", "n°7"), ou le surnom / pseudo
     * d'un contact du carnet de l'expéditeur. Les pseudos hors carnet ne sont PAS acceptés :
     * les numéros ne sont pas publics.
     */
    public static Optional<UUID> resolveRecipient(OwlPostData data, UUID sender, String raw) {
        if (raw == null) return Optional.empty();
        String q = raw.trim();
        if (q.isEmpty()) return Optional.empty();
        String digits = q.replaceFirst("^(?i)(#|n°|no\\.?|n\\.)\\s*", "");
        if (digits.matches("\\d{1,9}")) {
            return data.findByNumber(Integer.parseInt(digits));
        }
        String lower = q.toLowerCase(Locale.ROOT);
        for (Integer number : data.contacts(sender).keySet()) {
            String display = data.contactDisplayName(sender, number).toLowerCase(Locale.ROOT);
            Optional<UUID> id = data.findByNumber(number);
            String realName = id.map(data::getName).orElse("").toLowerCase(Locale.ROOT);
            if (display.equals(lower) || realName.equals(lower)) {
                return id;
            }
        }
        return Optional.empty();
    }

    private static LetterContent label(ServerPlayer sender, OwlPostData data, UUID recipientId, String title, String body) {
        int senderNumber = data.getOrAssign(sender.getUUID(), sender.getGameProfile().getName());
        return new LetterContent(
                sender.getGameProfile().getName(), senderNumber,
                data.getName(recipientId), data.getNumber(recipientId),
                title, body, System.currentTimeMillis());
    }

    private static void dispatch(ServerPlayer sender, OwlPostData data, UUID recipientId,
                                 ItemStack item, Kind kind, LetterContent label) {
        long now = System.currentTimeMillis();
        long deliverAt = now + Config.DELIVERY_DELAY_SECONDS.get() * 1000L;
        data.addPending(new OwlPostData.PendingLetter(recipientId, sender.getUUID(), item,
                kind == Kind.PARCEL, label, deliverAt));
        LAST_SEND.put(sender.getUUID(), now);

        sender.playNotifySound(SoundEvents.PARROT_FLY, SoundSource.PLAYERS, 1.0F, kind == Kind.PARCEL ? 0.5F : 0.7F);
        sender.serverLevel().sendParticles(kind == Kind.BEUGLANTE ? ParticleTypes.SMOKE : ParticleTypes.CLOUD,
                sender.getX(), sender.getY() + 1.8, sender.getZ(), 6, 0.25, 0.15, 0.25, 0.02);
    }

    private static void absentNotice(ServerPlayer sender, UUID recipientId, Kind kind) {
        if (sender.server.getPlayerList().getPlayer(recipientId) != null) return;
        boolean mailbox = OwlPostData.get(sender.server).hasMailbox(recipientId);
        String text;
        if (kind == Kind.BEUGLANTE) {
            text = "Le destinataire est absent du royaume ; la beuglante attendra son retour pour lui hurler dessus.";
        } else if (mailbox) {
            text = "Le destinataire est absent du royaume ; votre hibou déposera l'envoi dans sa boîte aux lettres.";
        } else {
            text = "Le destinataire est absent du royaume et n'a point de boîte aux lettres ; votre hibou attendra patiemment son retour.";
        }
        sender.sendSystemMessage(Msg.prefix().append(Msg.italic(text)));
    }

    private static void fail(ServerPlayer player, String reason, Kind kind) {
        player.sendSystemMessage(Msg.error(reason));
        int count = switch (kind) {
            case PARCEL -> count(player, ModItems.PARCEL.get());
            case BEUGLANTE -> beuglantesAvailable(player);
            default -> count(player, ModItems.BLANK_LETTER.get());
        };
        sendToModded(player, new Payloads.SendResult(false, reason, count));
    }

    public static int count(ServerPlayer player, Item item) {
        Inventory inv = player.getInventory();
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) count += s.getCount();
        }
        return count;
    }

    private static boolean consumeOne(ServerPlayer player, Item item) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && !s.isEmpty()) {
                s.shrink(1);
                inv.setChanged();
                return true;
            }
        }
        return false;
    }

    /** "3× Diamant, 1× Épée en fer" */
    public static String describe(List<ItemStack> items) {
        List<String> parts = new ArrayList<>();
        for (ItemStack s : items) {
            if (!s.isEmpty()) parts.add(s.getCount() + "× " + s.getHoverName().getString());
        }
        return String.join(", ", parts);
    }

    private static String safePrefill(String prefill) {
        String s = prefill == null ? "" : prefill.trim();
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    /** Retire les codes de couleur (§) et caractères de contrôle, coupe à la longueur max. */
    public static String sanitize(String in, int max, boolean allowNewlines) {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder(Math.min(in.length(), max));
        for (int i = 0; i < in.length() && sb.length() < max; i++) {
            char ch = in.charAt(i);
            if (ch == '§') continue;
            if (ch == '\n') {
                sb.append(allowNewlines ? '\n' : ' ');
                continue;
            }
            if (Character.isISOControl(ch)) continue;
            sb.append(ch);
        }
        return sb.toString().strip();
    }
}
