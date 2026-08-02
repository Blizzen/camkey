package online.blizzen.camkey.playback;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import online.blizzen.camkey.camera.Keyframe;

/**
 * Pure path math: maps overall progress t in [0,1] to a camera pose along the
 * keyframes. Time is distance-weighted (a 40-block leg gets twice the time of
 * a 20-block leg) so the camera moves at constant speed instead of pausing at
 * every keyframe. Yaw interpolates by shortest path, so 350 to 10 degrees
 * turns 20 degrees through north rather than 340 the wrong way around.
 *
 * Zero-length segments (rotation in place) get a small minimum weight so a
 * pure look-around sequence still plays instead of dividing by zero.
 */
public final class CameraPath {

    private static final double MIN_SEGMENT_WEIGHT = 1.0e-4;

    private final List<Keyframe> frames;
    private final double[] cumulativeWeight;
    private final double totalWeight;

    /** Requires at least 2 keyframes; the command layer enforces that. */
    public CameraPath(List<Keyframe> frames) {
        this.frames = List.copyOf(frames);
        this.cumulativeWeight = new double[frames.size()];
        double total = 0.0;
        for (int i = 1; i < frames.size(); i++) {
            total += Math.max(frames.get(i - 1).distanceTo(frames.get(i)), MIN_SEGMENT_WEIGHT);
            cumulativeWeight[i] = total;
        }
        this.totalWeight = total;
    }

    public record Pose(Vec3 position, float yaw, float pitch) {
    }

    public Pose sample(double t) {
        double s = Mth.clamp(t, 0.0, 1.0) * totalWeight;
        int segment = 1;
        while (segment < cumulativeWeight.length - 1 && cumulativeWeight[segment] < s) {
            segment++;
        }
        Keyframe a = frames.get(segment - 1);
        Keyframe b = frames.get(segment);
        double segmentStart = cumulativeWeight[segment - 1];
        double segmentLength = cumulativeWeight[segment] - segmentStart;
        double u = segmentLength <= 0.0 ? 0.0 : (s - segmentStart) / segmentLength;

        Vec3 position = a.position().lerp(b.position(), u);
        float yaw = (float) (a.yaw() + Mth.wrapDegrees(b.yaw() - a.yaw()) * u);
        float pitch = (float) (a.pitch() + (b.pitch() - a.pitch()) * u);
        return new Pose(position, yaw, pitch);
    }
}
