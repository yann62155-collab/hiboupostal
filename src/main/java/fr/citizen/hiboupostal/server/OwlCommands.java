package fr.citizen.hiboupostal.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.citizen.hiboupostal.data.OwlPostData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Commandes /hibou. Toutes utilisables sur PC comme sur console (Xbox).
 *
 * /hibou                              ouvre la volière
 * /hibou numero                       votre numéro de hibou
 * /hibou sceau <joueur>               tendre votre numéro à un joueur proche
 * /hibou ajouter <numéro> [surnom]    inscrire un contact dans votre carnet
 * /hibou retirer <numéro ou contact>  rayer un contact
 * /hibou contacts                     lister votre carnet
 * /hibou envoyer <dest> [objet]       envoyer le livre et plume tenu en main
 * /hibou ecrire <dest>                volière PC avec destinataire prérempli
 * /hibou colis <dest>                 expédier un carton (5 emplacements)
 * /hibou beugler <dest> <cri>         envoyer une beuglante
 * /hibou aide
 * Admin : /hibou chercher <pseudo>, /hibou console
 */
public final class OwlCommands {
    private OwlCommands() {}

    /** Suggestions : numéros et noms des contacts du carnet du joueur. */
    private static final SuggestionProvider<CommandSourceStack> CONTACTS = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        List<String> list = new ArrayList<>();
        if (player != null) {
            OwlPostData data = OwlPostData.get(player.server);
            for (Integer n : data.contacts(player.getUUID()).keySet()) {
                list.add(String.valueOf(n));
                String name = data.contactDisplayName(player.getUUID(), n);
                if (!name.contains(" ")) list.add(name);
            }
        }
        return SharedSuggestionProvider.suggest(list, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hibou")
                .executes(ctx -> open(ctx, ""))
                .then(Commands.literal("numero").executes(OwlCommands::myNumber))
                .then(Commands.literal("aide").executes(OwlCommands::help))
                .then(Commands.literal("contacts").executes(OwlCommands::listContacts))
                .then(Commands.literal("sceau")
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .executes(ctx -> {
                                    OwlPostService.giveSeal(ctx.getSource().getPlayerOrException(),
                                            EntityArgument.getPlayer(ctx, "joueur"));
                                    return 1;
                                })))
                .then(Commands.literal("ajouter")
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1))
                                .executes(ctx -> add(ctx, ""))
                                .then(Commands.argument("surnom", StringArgumentType.greedyString())
                                        .executes(ctx -> add(ctx, StringArgumentType.getString(ctx, "surnom"))))))
                .then(Commands.literal("retirer")
                        .then(Commands.argument("contact", StringArgumentType.greedyString())
                                .suggests(CONTACTS)
                                .executes(OwlCommands::remove)))
                .then(Commands.literal("envoyer")
                        .then(Commands.argument("destinataire", StringArgumentType.string())
                                .suggests(CONTACTS)
                                .executes(ctx -> sendBook(ctx, ""))
                                .then(Commands.argument("objet", StringArgumentType.greedyString())
                                        .executes(ctx -> sendBook(ctx, StringArgumentType.getString(ctx, "objet"))))))
                .then(Commands.literal("ecrire")
                        .then(Commands.argument("destinataire", StringArgumentType.greedyString())
                                .suggests(CONTACTS)
                                .executes(ctx -> open(ctx, StringArgumentType.getString(ctx, "destinataire")))))
                .then(Commands.literal("colis")
                        .executes(ctx -> parcel(ctx, ""))
                        .then(Commands.argument("destinataire", StringArgumentType.greedyString())
                                .suggests(CONTACTS)
                                .executes(ctx -> parcel(ctx, StringArgumentType.getString(ctx, "destinataire")))))
                .then(Commands.literal("beugler")
                        .then(Commands.argument("destinataire", StringArgumentType.string())
                                .suggests(CONTACTS)
                                .then(Commands.argument("cri", StringArgumentType.greedyString())
                                        .executes(OwlCommands::beugler))))
                // --- réservé aux administrateurs ---
                .then(Commands.literal("chercher")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("joueur", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        ctx.getSource().getServer().getPlayerNames(), builder))
                                .executes(OwlCommands::find)))
                .then(Commands.literal("console")
                        .requires(src -> src.hasPermission(2))
                        .executes(OwlCommands::toggleConsole)));
    }

    private static int open(CommandContext<CommandSourceStack> ctx, String prefill) throws CommandSyntaxException {
        OwlPostService.openOwlery(ctx.getSource().getPlayerOrException(), prefill);
        return 1;
    }

    private static int parcel(CommandContext<CommandSourceStack> ctx, String prefill) throws CommandSyntaxException {
        OwlPostService.openParcel(ctx.getSource().getPlayerOrException(), prefill);
        return 1;
    }

    private static int sendBook(CommandContext<CommandSourceStack> ctx, String subject) throws CommandSyntaxException {
        OwlPostService.sendBookInHand(ctx.getSource().getPlayerOrException(),
                StringArgumentType.getString(ctx, "destinataire"), subject);
        return 1;
    }

    private static int beugler(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        OwlPostService.sendBeuglante(ctx.getSource().getPlayerOrException(),
                StringArgumentType.getString(ctx, "destinataire"),
                StringArgumentType.getString(ctx, "cri"), null);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> ctx, String nickname) throws CommandSyntaxException {
        return OwlPostService.addContact(ctx.getSource().getPlayerOrException(),
                IntegerArgumentType.getInteger(ctx, "numero"), nickname) ? 1 : 0;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        OwlPostData data = OwlPostData.get(player.server);
        String query = StringArgumentType.getString(ctx, "contact").trim();
        Optional<UUID> id = OwlPostService.resolveRecipient(data, player.getUUID(), query);
        if (id.isEmpty()) {
            player.sendSystemMessage(Msg.error("Ce contact n'est pas dans votre carnet."));
            return 0;
        }
        return OwlPostService.removeContact(player, data.getNumber(id.get())) ? 1 : 0;
    }

    private static int listContacts(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        OwlPostData data = OwlPostData.get(player.server);
        Map<Integer, String> book = data.contacts(player.getUUID());
        if (book.isEmpty()) {
            player.sendSystemMessage(Msg.prefix().append(Msg.text("Votre carnet est vide. Demandez son numéro à un joueur puis tapez "))
                    .append(Msg.command("/hibou ajouter <numéro>")));
            return 0;
        }
        player.sendSystemMessage(Msg.prefix().append(Msg.flavor("Votre carnet de contacts (" + book.size() + ")")));
        for (Integer n : book.keySet()) {
            Optional<UUID> id = data.findByNumber(n);
            boolean online = id.isPresent() && player.server.getPlayerList().getPlayer(id.get()) != null;
            player.sendSystemMessage(Msg.text(online ? " ● " : " ○ ")
                    .append(Msg.number(n))
                    .append(Msg.text(" "))
                    .append(Msg.name(data.contactDisplayName(player.getUUID(), n))));
        }
        return book.size();
    }

    private static int myNumber(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int n = OwlPostData.get(player.server).getOrAssign(player.getUUID(), player.getGameProfile().getName());
        player.sendSystemMessage(Msg.prefix()
                .append(Msg.text("Votre fidèle hibou porte le "))
                .append(Msg.number(n))
                .append(Msg.text(". Ce numéro est secret : donnez-le avec "))
                .append(Msg.command("/hibou sceau <joueur>")));
        return n;
    }

    private static int find(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String query = StringArgumentType.getString(ctx, "joueur");
        OwlPostData data = OwlPostData.get(player.server);
        Optional<UUID> found = data.findByName(query);
        if (found.isEmpty()) {
            player.sendSystemMessage(Msg.error("Les registres de la volière ne mentionnent nul « " + query + " »."));
            return 0;
        }
        UUID id = found.get();
        int n = data.getNumber(id);
        player.sendSystemMessage(Msg.prefix()
                .append(Msg.text("[Admin] "))
                .append(Msg.name(data.getName(id)))
                .append(Msg.text(" possède le hibou "))
                .append(Msg.number(n))
                .append(Msg.text(".")));
        return n;
    }

    private static int toggleConsole(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean on = OwlPostService.toggleForcedConsole(player);
        player.sendSystemMessage(Msg.prefix().append(Msg.text(on
                ? "Mode console activé : vous voyez la volière comme un joueur Xbox."
                : "Mode console désactivé.")));
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        player.sendSystemMessage(Msg.prefix().append(Msg.flavor("Le Grimoire de la Volière")));
        line(player, "/hibou", "ouvrir la volière");
        line(player, "/hibou numero", "votre numéro de hibou (secret)");
        line(player, "/hibou sceau <joueur>", "donner votre numéro à un joueur proche");
        line(player, "/hibou ajouter <numéro> [surnom]", "inscrire un contact");
        line(player, "/hibou retirer <contact>", "rayer un contact");
        line(player, "/hibou contacts", "votre carnet");
        line(player, "/hibou envoyer <contact> [objet]", "envoyer le livre et plume tenu en main");
        line(player, "/hibou colis <contact>", "expédier un carton (5 emplacements)");
        line(player, "/hibou beugler <contact> <cri>", "envoyer une beuglante");
        player.sendSystemMessage(Msg.italic(" Lettre : une lettre vierge (papier + plume) ou un livre et plume."));
        player.sendSystemMessage(Msg.italic(" Beuglante : une beuglante vierge, ou 1 papier + 1 poudre à canon."));
        player.sendSystemMessage(Msg.italic(" Posez une boîte aux lettres : le courrier y arrive quand vous êtes absent ou que votre besace est pleine."));
        return 1;
    }

    private static void line(ServerPlayer player, String cmd, String what) {
        player.sendSystemMessage(Msg.text(" ").append(Msg.command(cmd)).append(Msg.italic(" — " + what)));
    }
}
