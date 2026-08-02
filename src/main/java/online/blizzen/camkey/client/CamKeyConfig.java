package online.blizzen.camkey.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config: the two knobs an operator actually needs. Everything else is
 * a command argument.
 */
public final class CamKeyConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue DEFAULT_PLAYBACK_SECONDS;
    public static final ModConfigSpec.BooleanValue EASING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DEFAULT_PLAYBACK_SECONDS = builder
                .comment("Playback duration in seconds when /camkey play is run without a duration.")
                .defineInRange("defaultPlaybackSeconds", 10.0, 0.1, 3600.0);
        EASING = builder
                .comment("Ease the camera in and out (smoothstep). Off = constant speed the whole way.")
                .define("easing", true);
        SPEC = builder.build();
    }

    private CamKeyConfig() {
    }
}
