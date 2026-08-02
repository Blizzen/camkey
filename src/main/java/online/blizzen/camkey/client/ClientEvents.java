package online.blizzen.camkey.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import online.blizzen.camkey.CamKey;
import online.blizzen.camkey.command.CamKeyCommands;
import online.blizzen.camkey.playback.PlaybackController;
import online.blizzen.camkey.store.SequenceStore;

/**
 * Game-bus client events: command registration, the playback tick, and the
 * safety hooks that guarantee the camera is always handed back to the player
 * and the store never outlives its world.
 */
@EventBusSubscriber(modid = CamKey.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CamKeyCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onClientTickPost(ClientTickEvent.Post event) {
        PlaybackController.tick();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PlaybackController.abort();
        SequenceStore.invalidate();
    }
}
