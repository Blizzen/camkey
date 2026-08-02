package online.blizzen.camkey.playback;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import online.blizzen.camkey.CamKey;
import online.blizzen.camkey.camera.DollyCameraEntity;
import online.blizzen.camkey.camera.Sequence;
import online.blizzen.camkey.client.CamKeyConfig;

/**
 * The playback state machine. Owns the dolly entity and the camera handoff:
 * spawn dolly at the first pose, point the game camera at it, advance it one
 * pose per client tick (the renderer interpolates between ticks), and always
 * hand the camera back, on finish, on stop, on interruption by a new play,
 * and on world exit.
 */
public final class PlaybackController {

    private static DollyCameraEntity dolly;
    private static CameraPath path;
    private static boolean eased;
    private static int totalTicks;
    private static int elapsed;
    private static String sequenceName;

    private PlaybackController() {
    }

    public static boolean isPlaying() {
        return dolly != null;
    }

    public static String playingName() {
        return sequenceName;
    }

    /** Starts playback, replacing any playback already running. */
    public static void start(String name, Sequence sequence, double seconds) {
        Minecraft mc = Minecraft.getInstance();
        stopInternal();

        path = new CameraPath(sequence.keyframes());
        eased = CamKeyConfig.EASING.get();
        totalTicks = Math.max(1, (int) Math.round(seconds * 20.0));
        elapsed = 0;
        sequenceName = name;

        dolly = new DollyCameraEntity(CamKey.DOLLY.get(), mc.level);
        apply(path.sample(0.0));
        dolly.setOldPosAndRot();
        mc.level.addEntity(dolly);
        mc.setCameraEntity(dolly);
    }

    /** Called every client tick (post). */
    public static void tick() {
        if (dolly == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stopInternal();
            return;
        }
        // The dolly lives in one level. Dimension change, death respawn, or a
        // void-floor discard replaces or removes it; end cleanly, not zombie.
        if (dolly.isRemoved() || dolly.level() != mc.level) {
            String stranded = sequenceName;
            stopInternal();
            mc.player.displayClientMessage(Component.literal(
                    "Playback of '" + stranded + "' stopped: the world changed. Camera returned."), false);
            return;
        }
        // Paused game = frozen take. Resume where we left off on unpause.
        if (mc.isPaused()) {
            return;
        }
        elapsed++;
        double t = Math.min(1.0, elapsed / (double) totalTicks);
        apply(path.sample(eased ? smoothstep(t) : t));
        if (elapsed >= totalTicks) {
            String finished = sequenceName;
            stopInternal();
            mc.player.displayClientMessage(
                    Component.literal("Finished '" + finished + "'. Camera returned."), false);
        }
    }

    /** User-initiated stop. Returns false if nothing was playing. */
    public static boolean stop() {
        if (dolly == null) {
            return false;
        }
        stopInternal();
        return true;
    }

    /** Safety abort (world exit, dimension gone). Never throws. */
    public static void abort() {
        stopInternal();
    }

    private static void apply(CameraPath.Pose pose) {
        dolly.setPos(pose.position().x, pose.position().y, pose.position().z);
        dolly.setYRot(pose.yaw());
        dolly.setXRot(pose.pitch());
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static void stopInternal() {
        Minecraft mc = Minecraft.getInstance();
        if (dolly != null) {
            if (mc.getCameraEntity() == dolly) {
                mc.setCameraEntity(mc.player);
            }
            dolly.discard();
        }
        dolly = null;
        path = null;
        sequenceName = null;
    }
}
