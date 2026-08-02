package online.blizzen.camkey.camera;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * An ordered list of keyframes recorded in one dimension. Immutable; edits
 * return a new Sequence so the store fully controls when state changes hit
 * disk. The dimension is stored as its id string (e.g. "minecraft:overworld")
 * so playback can refuse to run in the wrong dimension with a clear message.
 */
public record Sequence(String dimension, List<Keyframe> keyframes) {

    public static final Codec<Sequence> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(Sequence::dimension),
            Keyframe.CODEC.listOf().fieldOf("keyframes").forGetter(Sequence::keyframes)
    ).apply(instance, Sequence::new));

    public Sequence {
        keyframes = List.copyOf(keyframes);
    }

    public Sequence withKeyframe(Keyframe keyframe) {
        List<Keyframe> next = new ArrayList<>(keyframes);
        next.add(keyframe);
        return new Sequence(dimension, next);
    }

    /** @param index zero-based */
    public Sequence withoutKeyframe(int index) {
        List<Keyframe> next = new ArrayList<>(keyframes);
        next.remove(index);
        return new Sequence(dimension, next);
    }

    public int size() {
        return keyframes.size();
    }

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }
}
