package fr.citizen.hiboupostal.item;

import fr.citizen.hiboupostal.HibouPostal;
import fr.citizen.hiboupostal.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HibouPostal.MODID);

    /** Lettre vierge : consommée à chaque envoi. */
    public static final DeferredItem<BlankLetterItem> BLANK_LETTER = ITEMS.register("blank_letter",
            () -> new BlankLetterItem(new Item.Properties().stacksTo(16)));

    /** Lettre scellée : remise par le hibou au destinataire. */
    public static final DeferredItem<SealedLetterItem> SEALED_LETTER = ITEMS.register("sealed_letter",
            () -> new SealedLetterItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Carton vide (craftable) : consommé à chaque envoi de colis. */
    public static final DeferredItem<ParcelItem> PARCEL = ITEMS.register("parcel",
            () -> new ParcelItem(new Item.Properties().stacksTo(16)));

    /** Carton scellé : contient jusqu'à 5 piles d'objets, remis par le hibou. */
    public static final DeferredItem<SealedParcelItem> SEALED_PARCEL = ITEMS.register("sealed_parcel",
            () -> new SealedParcelItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Beuglante vierge : enveloppe rouge à remplir de cris. */
    public static final DeferredItem<BlankBeuglanteItem> BLANK_BEUGLANTE = ITEMS.register("blank_beuglante",
            () -> new BlankBeuglanteItem(new Item.Properties().stacksTo(16)));

    /** Beuglante scellée : hurle son message quand on l'ouvre (ou d'elle-même au bout d'un moment). */
    public static final DeferredItem<BeuglanteItem> BEUGLANTE = ITEMS.register("beuglante",
            () -> new BeuglanteItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** Boîte aux lettres (bloc). */
    public static final DeferredItem<BlockItem> MAILBOX = ITEMS.register("mailbox",
            () -> new BlockItem(ModBlocks.MAILBOX.get(), new Item.Properties()));

    private ModItems() {}
}
