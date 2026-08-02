package online.blizzen.camkey.camera;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

/**
 * One captured camera pose: the lens position (the player's eye position at
 * capture time) and the view rotation. Deliberately minimal; new fields
 * (roll, FOV, hold time) can be added behind the file's schemaVersion.
 */
public record Keyframe(double x, double y, double z, float yaw, float pitch) {

    public static final Codec<Keyframe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(Keyframe::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Keyframe::y),
            Codec.DOUBLE.fieldOf("z").forGetter(Keyframe::z),
            Codec.FLOAT.fieldOf("yaw").forGetter(Keyframe::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(Keyframe::pitch)
    ).apply(instance, Keyframe::new));

    public Vec3 position() {
        return new Vec3(x, y, z);
    }

    public double distanceTo(Keyframe other) {
        double dx = other.x - x;
        double dy = other.y - y;
        double dz = other.z - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
