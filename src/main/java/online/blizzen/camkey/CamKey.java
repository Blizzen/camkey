package online.blizzen.camkey;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import online.blizzen.camkey.camera.DollyCameraEntity;

/**
 * Common entry point. CamKey is a client-side tool; the only thing that must
 * live here is registry content, because registries exist on both sides.
 */
@Mod(CamKey.MODID)
public class CamKey {
    public static final String MODID = "camkey";

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    /**
     * The invisible entity the game camera rides during playback.
     * noSave/noSummon: it can never leak into a world save or be summoned.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<DollyCameraEntity>> DOLLY =
            ENTITY_TYPES.register("dolly", () -> EntityType.Builder
                    .<DollyCameraEntity>of(DollyCameraEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .eyeHeight(0.0f)
                    .noSave()
                    .noSummon()
                    .build("dolly"));

    public CamKey(IEventBus modEventBus, ModContainer modContainer) {
        ENTITY_TYPES.register(modEventBus);
    }
}
