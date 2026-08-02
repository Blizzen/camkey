package online.blizzen.camkey.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import online.blizzen.camkey.CamKey;

/**
 * Game-bus client events: command registration, the playback tick, and the
 * safety hooks that guarantee the camera is always handed back to the player.
 */
@EventBusSubscriber(modid = CamKey.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("camkey")
                .then(Commands.literal("spike")
                        .then(Commands.argument("seconds", FloatArgumentType.floatArg(1.0f, 60.0f))
                                .executes(ctx -> SpikeFlight.start(ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "seconds"))))
                        .executes(ctx -> SpikeFlight.start(ctx.getSource(), 8.0f))));
    }

    @SubscribeEvent
    static void onClientTickPost(ClientTickEvent.Post event) {
        SpikeFlight.tick();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SpikeFlight.abort();
    }
}
