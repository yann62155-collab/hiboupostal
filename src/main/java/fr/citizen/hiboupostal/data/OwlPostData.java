package fr.citizen.hiboupostal.data;

import fr.citizen.hiboupostal.HibouPostal;
import fr.citizen.hiboupostal.item.LetterContent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Données persistantes du mod (fichier world/data/hiboupostal.dat) :
 * - le registre des hiboux (UUID joueur -> numéro de hibou, dernier pseudo connu)
 * - les lettres et cartons en vol / en attente de remise.
 */
public class OwlPostData extends SavedData {
    private static final String FILE_ID = HibouPostal.MODID;

    public static final SavedData.Factory<OwlPostData> FACTORY =
            new SavedData.Factory<>(OwlPostData::new, OwlPostData::load, null);

    /**
     * Un envoi en vol vers son destinataire : lettre scellée ou carton scellé.
     * {@code item} est l'objet exact qui sera remis ; {@code label} sert aux messages.
     */
    public record PendingLetter(UUID recipient, UUID sender, ItemStack item, boolean parcel,
                                LetterContent label, long deliverAt) {}

    private final Map<UUID, Integer> numbers = new HashMap<>();
    private final Map<Integer, UUID> byNumber = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final List<PendingLetter> pending = new ArrayList<>();
    private int nextNumber = 1;

    /** Taille d'une boîte aux lettres (3 rangées de 9). */
    public static final int MAILBOX_SIZE = 27;
    /** Contenu des boîtes aux lettres, par propriétaire (partagé entre toutes ses boîtes). */
    private final Map<UUID, NonNullList<ItemStack>> mailboxes = new HashMap<>();
    /** Carnet de contacts privé de chaque joueur : numéro de hibou -> surnom ("" = pseudo du joueur). */
    private final Map<UUID, LinkedHashMap<Integer, String>> contacts = new HashMap<>();
    /** Boîtes aux lettres posées dans le monde : propriétaire -> positions ("dimension;pos"). */
    private final Map<UUID, Set<String>> mailboxPositions = new HashMap<>();

    public static OwlPostData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    // ------------------------------------------------------------------
    // Registre des hiboux
    // ------------------------------------------------------------------

    /** Retourne le numéro de hibou du joueur, en lui en attribuant un s'il n'en a pas. */
    public int getOrAssign(UUID uuid, String name) {
        Integer existing = numbers.get(uuid);
        if (!name.equals(names.get(uuid))) {
            names.put(uuid, name);
            setDirty();
        }
        if (existing != null) {
            return existing;
        }
        int number = nextNumber++;
        while (byNumber.containsKey(number)) {
            number = nextNumber++;
        }
        numbers.put(uuid, number);
        byNumber.put(number, uuid);
        setDirty();
        return number;
    }

    public boolean hasNumber(UUID uuid) {
        return numbers.containsKey(uuid);
    }

    public int getNumber(UUID uuid) {
        return numbers.getOrDefault(uuid, 0);
    }

    public Optional<UUID> findByNumber(int number) {
        return Optional.ofNullable(byNumber.get(number));
    }

    public Optional<UUID> findByName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return names.entrySet().stream()
                .filter(e -> e.getValue().toLowerCase(Locale.ROOT).equals(lower))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public String getName(UUID uuid) {
        return names.getOrDefault(uuid, "?");
    }

    public Map<UUID, Integer> allNumbers() {
        return numbers;
    }

    // ------------------------------------------------------------------
    // Lettres en vol
    // ------------------------------------------------------------------

    public void addPending(PendingLetter letter) {
        pending.add(letter);
        setDirty();
    }

    public List<PendingLetter> pending() {
        return pending;
    }

    public void removePending(PendingLetter letter) {
        if (pending.remove(letter)) {
            setDirty();
        }
    }

    public int countPendingFor(UUID recipient) {
        int n = 0;
        for (PendingLetter p : pending) {
            if (p.recipient().equals(recipient)) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------
    // Carnet de contacts (les numéros ne sont plus publics)
    // ------------------------------------------------------------------

    /** Contacts du joueur, dans l'ordre d'ajout (numéro -> surnom). */
    public Map<Integer, String> contacts(UUID owner) {
        return contacts.computeIfAbsent(owner, k -> new LinkedHashMap<>());
    }

    public boolean hasContact(UUID owner, int number) {
        Map<Integer, String> map = contacts.get(owner);
        return map != null && map.containsKey(number);
    }

    public void addContact(UUID owner, int number, String nickname) {
        contacts(owner).put(number, nickname == null ? "" : nickname);
        setDirty();
    }

    public boolean removeContact(UUID owner, int number) {
        Map<Integer, String> map = contacts.get(owner);
        if (map != null && map.remove(number) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    /** Nom affiché d'un contact : son surnom s'il en a un, sinon le pseudo actuel du joueur. */
    public String contactDisplayName(UUID owner, int number) {
        String nick = contacts(owner).getOrDefault(number, "");
        if (!nick.isBlank()) return nick;
        return findByNumber(number).map(this::getName).orElse("?");
    }

    // ------------------------------------------------------------------
    // Boîtes aux lettres
    // ------------------------------------------------------------------

    public static String posKey(String dimension, long pos) {
        return dimension + ";" + pos;
    }

    public void registerMailbox(UUID owner, String key) {
        if (mailboxPositions.computeIfAbsent(owner, k -> new HashSet<>()).add(key)) setDirty();
    }

    public void unregisterMailbox(UUID owner, String key) {
        Set<String> set = mailboxPositions.get(owner);
        if (set != null && set.remove(key)) {
            if (set.isEmpty()) mailboxPositions.remove(owner);
            setDirty();
        }
    }

    /** Le joueur possède-t-il au moins une boîte aux lettres posée dans le monde ? */
    public boolean hasMailbox(UUID owner) {
        Set<String> set = mailboxPositions.get(owner);
        return set != null && !set.isEmpty();
    }

    /** Contenu (modifiable) de la boîte aux lettres d'un joueur. */
    public NonNullList<ItemStack> mailbox(UUID owner) {
        return mailboxes.computeIfAbsent(owner, k -> NonNullList.withSize(MAILBOX_SIZE, ItemStack.EMPTY));
    }

    /** Dépose un objet dans la boîte ; false si elle est pleine. */
    public boolean storeInMailbox(UUID owner, ItemStack stack) {
        NonNullList<ItemStack> box = mailbox(owner);
        for (int i = 0; i < box.size(); i++) {
            if (box.get(i).isEmpty()) {
                box.set(i, stack);
                setDirty();
                return true;
            }
        }
        return false;
    }

    /** Nombre d'objets d'un type donné dans la boîte. */
    public int countInMailbox(UUID owner, java.util.function.Predicate<ItemStack> filter) {
        NonNullList<ItemStack> box = mailboxes.get(owner);
        if (box == null) return 0;
        int n = 0;
        for (ItemStack s : box) {
            if (!s.isEmpty() && filter.test(s)) n++;
        }
        return n;
    }

    public void markDirty() {
        setDirty();
    }

    // ------------------------------------------------------------------
    // Sauvegarde / chargement
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next_number", nextNumber);

        ListTag owls = new ListTag();
        for (Map.Entry<UUID, Integer> e : numbers.entrySet()) {
            CompoundTag owl = new CompoundTag();
            owl.putUUID("uuid", e.getKey());
            owl.putInt("number", e.getValue());
            owl.putString("name", names.getOrDefault(e.getKey(), "?"));
            owls.add(owl);
        }
        tag.put("owls", owls);

        ListTag letters = new ListTag();
        for (PendingLetter p : pending) {
            CompoundTag l = new CompoundTag();
            l.putUUID("recipient", p.recipient());
            l.putUUID("sender", p.sender());
            l.putLong("deliver_at", p.deliverAt());
            l.putBoolean("parcel", p.parcel());
            LetterContent.CODEC.encodeStart(NbtOps.INSTANCE, p.label())
                    .resultOrPartial(err -> HibouPostal.LOGGER.error("Etiquette non sauvegardable : {}", err))
                    .ifPresent(t -> l.put("label", t));
            if (!p.item().isEmpty()) {
                l.put("item", p.item().save(registries));
            }
            if (l.contains("label") && l.contains("item")) {
                letters.add(l);
            }
        }
        tag.put("pending", letters);

        ListTag boxes = new ListTag();
        for (Map.Entry<UUID, NonNullList<ItemStack>> e : mailboxes.entrySet()) {
            boolean empty = e.getValue().stream().allMatch(ItemStack::isEmpty);
            if (empty) continue;
            CompoundTag b = new CompoundTag();
            b.putUUID("owner", e.getKey());
            ContainerHelper.saveAllItems(b, e.getValue(), registries);
            boxes.add(b);
        }
        tag.put("mailboxes", boxes);

        ListTag positions = new ListTag();
        for (Map.Entry<UUID, Set<String>> e : mailboxPositions.entrySet()) {
            for (String key : e.getValue()) {
                CompoundTag pt = new CompoundTag();
                pt.putUUID("owner", e.getKey());
                pt.putString("pos", key);
                positions.add(pt);
            }
        }
        tag.put("mailbox_positions", positions);

        ListTag book = new ListTag();
        for (Map.Entry<UUID, LinkedHashMap<Integer, String>> e : contacts.entrySet()) {
            for (Map.Entry<Integer, String> c : e.getValue().entrySet()) {
                CompoundTag ct = new CompoundTag();
                ct.putUUID("owner", e.getKey());
                ct.putInt("number", c.getKey());
                ct.putString("nickname", c.getValue());
                book.add(ct);
            }
        }
        tag.put("contacts", book);
        return tag;
    }

    private static OwlPostData load(CompoundTag tag, HolderLookup.Provider registries) {
        OwlPostData data = new OwlPostData();
        data.nextNumber = Math.max(1, tag.getInt("next_number"));

        ListTag owls = tag.getList("owls", Tag.TAG_COMPOUND);
        for (int i = 0; i < owls.size(); i++) {
            CompoundTag owl = owls.getCompound(i);
            if (!owl.hasUUID("uuid")) continue;
            UUID uuid = owl.getUUID("uuid");
            int number = owl.getInt("number");
            data.numbers.put(uuid, number);
            data.byNumber.put(number, uuid);
            data.names.put(uuid, owl.getString("name"));
            if (number >= data.nextNumber) {
                data.nextNumber = number + 1;
            }
        }

        ListTag letters = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < letters.size(); i++) {
            CompoundTag l = letters.getCompound(i);
            if (!l.hasUUID("recipient") || !l.hasUUID("sender") || !l.contains("label") || !l.contains("item")) continue;
            UUID recipient = l.getUUID("recipient");
            UUID sender = l.getUUID("sender");
            long deliverAt = l.getLong("deliver_at");
            boolean parcel = l.getBoolean("parcel");
            Optional<ItemStack> item = ItemStack.parse(registries, l.get("item"));
            Optional<LetterContent> label = LetterContent.CODEC.parse(NbtOps.INSTANCE, l.get("label"))
                    .resultOrPartial(err -> HibouPostal.LOGGER.error("Etiquette illisible : {}", err));
            if (item.isPresent() && label.isPresent()) {
                data.pending.add(new PendingLetter(recipient, sender, item.get(), parcel, label.get(), deliverAt));
            } else {
                HibouPostal.LOGGER.error("Envoi en vol illisible ignoré (destinataire {})", recipient);
            }
        }

        ListTag boxes = tag.getList("mailboxes", Tag.TAG_COMPOUND);
        for (int i = 0; i < boxes.size(); i++) {
            CompoundTag b = boxes.getCompound(i);
            if (!b.hasUUID("owner")) continue;
            NonNullList<ItemStack> list = NonNullList.withSize(MAILBOX_SIZE, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(b, list, registries);
            data.mailboxes.put(b.getUUID("owner"), list);
        }

        ListTag positions = tag.getList("mailbox_positions", Tag.TAG_COMPOUND);
        for (int i = 0; i < positions.size(); i++) {
            CompoundTag pt = positions.getCompound(i);
            if (!pt.hasUUID("owner")) continue;
            data.mailboxPositions.computeIfAbsent(pt.getUUID("owner"), k -> new HashSet<>()).add(pt.getString("pos"));
        }

        ListTag book = tag.getList("contacts", Tag.TAG_COMPOUND);
        for (int i = 0; i < book.size(); i++) {
            CompoundTag ct = book.getCompound(i);
            if (!ct.hasUUID("owner")) continue;
            data.contacts.computeIfAbsent(ct.getUUID("owner"), k -> new LinkedHashMap<>())
                    .put(ct.getInt("number"), ct.getString("nickname"));
        }
        return data;
    }
}
