package fr.citizen.hiboupostal.network;

import fr.citizen.hiboupostal.server.Beuglantes;
import fr.citizen.hiboupostal.server.OwlPostService;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.BiConsumer;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // optional() : les clients sans le mod (joueurs Xbox via Geyser) peuvent quand même se connecter
        PayloadRegistrar registrar = event.registrar("2").optional();

        // Serveur -> client (les classes client ne sont chargées que côté client via ClientBridge)
        registrar.playToClient(Payloads.OpenOwlery.TYPE, Payloads.OpenOwlery.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientBridge.openOwlery(payload)));
        registrar.playToClient(Payloads.SendResult.TYPE, Payloads.SendResult.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientBridge.handleSendResult(payload)));
        registrar.playToClient(Payloads.ContactsUpdate.TYPE, Payloads.ContactsUpdate.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientBridge.handleContacts(payload)));
        registrar.playToClient(Payloads.PlayBeuglante.TYPE, Payloads.PlayBeuglante.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientBridge.playBeuglante(payload)));

        // Client -> serveur
        server(registrar, Payloads.RequestOwlery.TYPE, Payloads.RequestOwlery.STREAM_CODEC,
                (player, p) -> OwlPostService.openOwlery(player, p.prefill()));
        server(registrar, Payloads.SendLetter.TYPE, Payloads.SendLetter.STREAM_CODEC,
                OwlPostService::handleSend);
        server(registrar, Payloads.RequestParcel.TYPE, Payloads.RequestParcel.STREAM_CODEC,
                (player, p) -> OwlPostService.openParcel(player, p.prefill()));
        server(registrar, Payloads.SendParcel.TYPE, Payloads.SendParcel.STREAM_CODEC,
                OwlPostService::handleSendParcel);
        server(registrar, Payloads.AddContact.TYPE, Payloads.AddContact.STREAM_CODEC,
                (player, p) -> OwlPostService.addContact(player, p.number(), p.nickname()));
        server(registrar, Payloads.RemoveContact.TYPE, Payloads.RemoveContact.STREAM_CODEC,
                (player, p) -> OwlPostService.removeContact(player, p.number()));
        server(registrar, Payloads.BeuglanteChunk.TYPE, Payloads.BeuglanteChunk.STREAM_CODEC,
                Beuglantes::receiveChunk);
        server(registrar, Payloads.SendBeuglante.TYPE, Payloads.SendBeuglante.STREAM_CODEC,
                OwlPostService::handleSendBeuglante);
    }

    private static <T extends CustomPacketPayload> void server(PayloadRegistrar registrar, CustomPacketPayload.Type<T> type,
                                                                StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                BiConsumer<ServerPlayer, T> handler) {
        registrar.playToServer(type, codec, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                handler.accept(player, payload);
            }
        }));
    }
}
