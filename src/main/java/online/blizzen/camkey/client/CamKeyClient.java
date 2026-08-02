package online.blizzen.camkey.client;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import online.blizzen.camkey.CamKey;

/**
 * Client-only entry point. This class never loads on a dedicated server.
 */
@Mod(value = CamKey.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CamKey.MODID, value = Dist.CLIENT)
public class CamKeyClient {
    public CamKeyClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // The dolly is never visible, but every entity type needs a registered
        // renderer or the game crashes the moment one exists in a rendered world.
        event.registerEntityRenderer(CamKey.DOLLY.get(), NoopRenderer::new);
    }
}
