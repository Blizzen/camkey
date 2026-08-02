package online.blizzen.camkey.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import online.blizzen.camkey.CamKey;
import online.blizzen.camkey.camera.DollyCameraEntity;

/**
 * Throwaway spike that proves the dolly-entity camera rig end to end:
 * spawn client-side entity, point the camera at it, move it once per tick
 * (the renderer interpolates between ticks for free), hand the camera back.
 *
 * Flies one full circle around the player's eye position, always looking at
 * the player. Replaced by the real PlaybackController in the next phase.
 */
public final class SpikeFlight {
    private static final double RADIUS = 8.0;
    private static final double HEIGHT = 4.0;

    private static DollyCameraEntity dolly;
    private static Vec3 center;
    private static int totalTicks;
    private static int elapsed;

    private SpikeFlight() {
    }

    static int start(CommandSourceStack source, float seconds) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return 0;
        }
        abort();
        center = player.getEyePosition();
        totalTicks = Math.max(1, (int) (seconds * 20.0f));
        elapsed = 0;

        dolly = new DollyCameraEntity(CamKey.DOLLY.get(), mc.level);
        moveDolly(0.0f);
        dolly.setOldPosAndRot();
        mc.level.addEntity(dolly);
        mc.setCameraEntity(dolly);

        source.sendSuccess(() -> Component.literal(
                "CamKey spike: orbiting for " + seconds + "s"), false);
        return 1;
    }

    static void tick() {
        if (dolly == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            abort();
            return;
        }
        elapsed++;
        if (elapsed >= totalTicks) {
            finish("CamKey spike: done, camera returned");
            return;
        }
        moveDolly((float) elapsed / (float) totalTicks);
    }

    static void abort() {
        if (dolly != null) {
            finish(null);
        }
    }

    private static void moveDolly(float progress) {
        double angle = progress * Math.PI * 2.0;
        double x = center.x + Math.cos(angle) * RADIUS;
        double y = center.y + HEIGHT;
        double z = center.z + Math.sin(angle) * RADIUS;
        dolly.setPos(x, y, z);

        double dx = center.x - x;
        double dy = center.y - y;
        double dz = center.z - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        dolly.setYRot(yaw);
        dolly.setXRot(pitch);
    }

    private static void finish(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() == dolly) {
            mc.setCameraEntity(mc.player);
        }
        if (dolly != null) {
            dolly.discard();
        }
        dolly = null;
        center = null;
        if (message != null && mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
