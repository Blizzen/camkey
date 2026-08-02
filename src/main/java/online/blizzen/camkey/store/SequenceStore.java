package online.blizzen.camkey.store;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import online.blizzen.camkey.camera.Sequence;
import org.slf4j.Logger;

/**
 * Owns the on-disk sequence library for the world the player is currently in.
 *
 * Location: in singleplayer the file lives inside the world folder
 * (saves/&lt;world&gt;/camkey/sequences.json) so shot data travels with the
 * world when it is copied or shared. When connected to a remote server there
 * is no world folder, so it falls back to
 * config/camkey/by-server/&lt;address&gt;.json.
 *
 * Every mutation is written to disk immediately (temp file, then atomic
 * replace) so a crash can never lose captured shots. An unreadable file is
 * never deleted: it is renamed to .bak and the library starts fresh.
 */
public final class SequenceStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCHEMA_VERSION = 1;

    private record FileModel(int schemaVersion, Map<String, Sequence> sequences) {
        static final Codec<FileModel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("schemaVersion").forGetter(FileModel::schemaVersion),
                Codec.unboundedMap(Codec.STRING, Sequence.CODEC).fieldOf("sequences").forGetter(FileModel::sequences)
        ).apply(instance, FileModel::new));
    }

    private static SequenceStore active;

    private final Path path;
    private final Map<String, Sequence> sequences;
    private boolean recoveredFromBadFile;

    private SequenceStore(Path path, Map<String, Sequence> sequences, boolean recoveredFromBadFile) {
        this.path = path;
        this.sequences = sequences;
        this.recoveredFromBadFile = recoveredFromBadFile;
    }

    /** The store for the world the player is currently in. */
    public static SequenceStore get() {
        Path current = resolveCurrentPath();
        if (active == null || !active.path.equals(current)) {
            active = load(current);
        }
        return active;
    }

    /** Drop the cached store; called when the player leaves the world. */
    public static void invalidate() {
        active = null;
    }

    private static Path resolveCurrentPath() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            return server.getWorldPath(LevelResource.ROOT).resolve("camkey").resolve("sequences.json");
        }
        ServerData data = mc.getCurrentServer();
        String key = data != null ? data.ip.replaceAll("[^A-Za-z0-9._-]", "_") : "unknown";
        return FMLPaths.CONFIGDIR.get().resolve("camkey").resolve("by-server").resolve(key + ".json");
    }

    private static SequenceStore load(Path path) {
        if (!Files.exists(path)) {
            return new SequenceStore(path, new LinkedHashMap<>(), false);
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement json = JsonParser.parseString(raw);
            DataResult<FileModel> parsed = FileModel.CODEC.parse(JsonOps.INSTANCE, json);
            Optional<FileModel> model = parsed.resultOrPartial(
                    error -> LOGGER.warn("CamKey: bad sequences file {}: {}", path, error));
            if (model.isPresent() && model.get().schemaVersion() <= SCHEMA_VERSION) {
                return new SequenceStore(path, new LinkedHashMap<>(model.get().sequences()), false);
            }
            if (model.isPresent()) {
                LOGGER.warn("CamKey: {} has schemaVersion {} which is newer than this build understands ({})",
                        path, model.get().schemaVersion(), SCHEMA_VERSION);
            }
        } catch (Exception e) {
            LOGGER.warn("CamKey: could not read {}", path, e);
        }
        backUpBadFile(path);
        return new SequenceStore(path, new LinkedHashMap<>(), true);
    }

    private static void backUpBadFile(Path path) {
        try {
            Files.move(path, path.resolveSibling(path.getFileName() + ".bak"),
                    StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("CamKey: kept unreadable file as {}.bak", path.getFileName());
        } catch (IOException e) {
            LOGGER.error("CamKey: could not back up unreadable file {}", path, e);
        }
    }

    /** True exactly once after a bad-file recovery, so the user gets told once. */
    public boolean consumeRecoveryNotice() {
        boolean notice = recoveredFromBadFile;
        recoveredFromBadFile = false;
        return notice;
    }

    public List<String> names() {
        return List.copyOf(sequences.keySet());
    }

    public Optional<Sequence> find(String name) {
        return Optional.ofNullable(sequences.get(name));
    }

    /** Appends a keyframe, creating the sequence if needed. Returns the new size. */
    public int append(String name, String dimension, online.blizzen.camkey.camera.Keyframe keyframe) {
        Sequence sequence = sequences.get(name);
        sequence = sequence == null
                ? new Sequence(dimension, List.of(keyframe))
                : sequence.withKeyframe(keyframe);
        sequences.put(name, sequence);
        save();
        return sequence.size();
    }

    /**
     * Removes the keyframe at the zero-based index (or the last one if index
     * is -1). Deletes the sequence entirely if that empties it. Returns the
     * remaining size, or -1 if the sequence was deleted.
     */
    public int remove(String name, int index) {
        Sequence sequence = sequences.get(name);
        int target = index < 0 ? sequence.size() - 1 : index;
        Sequence next = sequence.withoutKeyframe(target);
        if (next.isEmpty()) {
            sequences.remove(name);
            save();
            return -1;
        }
        sequences.put(name, next);
        save();
        return next.size();
    }

    private void save() {
        try {
            JsonElement json = FileModel.CODEC
                    .encodeStart(JsonOps.INSTANCE, new FileModel(SCHEMA_VERSION, sequences))
                    .getOrThrow();
            String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(json);
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, pretty, StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.error("CamKey: failed to save {}", path, e);
        }
    }
}
