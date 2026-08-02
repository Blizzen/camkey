package online.blizzen.camkey.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import online.blizzen.camkey.camera.Keyframe;
import online.blizzen.camkey.camera.Sequence;
import online.blizzen.camkey.client.CamKeyConfig;
import online.blizzen.camkey.playback.PlaybackController;
import online.blizzen.camkey.store.SequenceStore;

/**
 * The /camkey command tree. All validation and user-facing messaging lives
 * here; the store and the playback controller only ever see valid input.
 */
public final class CamKeyCommands {

    private CamKeyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("camkey")
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> add(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("play")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CamKeyCommands::suggestNames)
                                .executes(ctx -> play(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        CamKeyConfig.DEFAULT_PLAYBACK_SECONDS.get()))
                                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.1, 3600.0))
                                        .executes(ctx -> play(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                DoubleArgumentType.getDouble(ctx, "seconds")))
                                        // The spec's example is "/camkey play intro 10 seconds";
                                        // accept the trailing word so the documented form works.
                                        .then(Commands.literal("seconds")
                                                .executes(ctx -> play(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        DoubleArgumentType.getDouble(ctx, "seconds")))))))
                .then(Commands.literal("stop")
                        .executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CamKeyCommands::suggestNames)
                                .executes(ctx -> remove(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"), -1))
                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(ctx -> remove(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "index")))))));
    }

    private static CompletableFuture<Suggestions> suggestNames(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(SequenceStore.get().names(), builder);
    }

    /** Fetches the store and surfaces the bad-file recovery notice once. */
    private static SequenceStore store(CommandSourceStack source) {
        SequenceStore store = SequenceStore.get();
        if (store.consumeRecoveryNotice()) {
            source.sendSystemMessage(Component.literal(
                    "CamKey could not read this world's sequence file and started fresh. "
                            + "The old file was kept next to it as sequences.json.bak."));
        }
        return store;
    }

    private static String currentDimension() {
        return Minecraft.getInstance().level.dimension().location().toString();
    }

    private static int add(CommandSourceStack source, String name) {
        if (PlaybackController.isPlaying()) {
            source.sendFailure(Component.literal(
                    "Can't edit sequences during playback. /camkey stop first."));
            return 0;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        SequenceStore store = store(source);
        String dimension = currentDimension();

        Optional<Sequence> existing = store.find(name);
        if (existing.isPresent() && !existing.get().dimension().equals(dimension)) {
            source.sendFailure(Component.literal(
                    "'" + name + "' was recorded in " + existing.get().dimension()
                            + " and you are in " + dimension
                            + ". One sequence stays in one dimension."));
            return 0;
        }

        Vec3 eye = player.getEyePosition();
        Keyframe keyframe = new Keyframe(eye.x, eye.y, eye.z, player.getYRot(), player.getXRot());
        int size = store.append(name, dimension, keyframe);
        source.sendSuccess(() -> Component.literal(size == 1
                ? "Created sequence '" + name + "'. Keyframe 1 captured."
                : "Keyframe " + size + " added to '" + name + "'."), false);
        warnIfSaveFailed(source, store);
        return 1;
    }

    private static void warnIfSaveFailed(CommandSourceStack source, SequenceStore store) {
        if (store.consumeSaveFailureNotice()) {
            source.sendFailure(Component.literal(
                    "Warning: that change is in memory but could not be written to disk "
                            + "(see the game log). It will retry on your next change."));
        }
    }

    private static int play(CommandSourceStack source, String name, double seconds) {
        SequenceStore store = store(source);
        Optional<Sequence> found = store.find(name);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal(
                    "No sequence named '" + name + "'. /camkey list shows what exists."));
            return 0;
        }
        Sequence sequence = found.get();
        if (sequence.size() < 2) {
            source.sendFailure(Component.literal(
                    "Sequence '" + name + "' has only 1 keyframe; it needs at least 2 to play."));
            return 0;
        }
        String dimension = currentDimension();
        if (!sequence.dimension().equals(dimension)) {
            source.sendFailure(Component.literal(
                    "'" + name + "' was recorded in " + sequence.dimension()
                            + " and you are in " + dimension + "."));
            return 0;
        }

        boolean replaced = PlaybackController.isPlaying();
        PlaybackController.start(name, sequence, seconds);
        source.sendSuccess(() -> Component.literal(
                (replaced ? "Replaced the running playback. " : "")
                        + "Playing '" + name + "': " + sequence.size() + " keyframes over "
                        + seconds + "s. /camkey stop ends it early."), false);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        if (PlaybackController.stop()) {
            source.sendSuccess(() -> Component.literal("Playback stopped. Camera returned."), false);
        } else {
            source.sendSuccess(() -> Component.literal("Nothing is playing."), false);
        }
        return 1;
    }

    private static int list(CommandSourceStack source) {
        SequenceStore store = store(source);
        if (store.names().isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "No sequences in this world yet. /camkey add <name> captures one."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Sequences in this world:"), false);
        for (String name : store.names()) {
            Sequence sequence = store.find(name).orElseThrow();
            source.sendSuccess(() -> Component.literal(
                    "  " + name + ": " + sequence.size() + " keyframes ("
                            + sequence.dimension() + ")"), false);
        }
        return 1;
    }

    private static int remove(CommandSourceStack source, String name, int oneBasedIndex) {
        if (PlaybackController.isPlaying()) {
            source.sendFailure(Component.literal(
                    "Can't edit sequences during playback. /camkey stop first."));
            return 0;
        }
        SequenceStore store = store(source);
        Optional<Sequence> found = store.find(name);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal(
                    "No sequence named '" + name + "'. /camkey list shows what exists."));
            return 0;
        }
        int size = found.get().size();
        if (oneBasedIndex > size) {
            source.sendFailure(Component.literal(
                    "Sequence '" + name + "' has " + size + " keyframes; there is no #"
                            + oneBasedIndex + "."));
            return 0;
        }
        int removedNumber = oneBasedIndex < 0 ? size : oneBasedIndex;
        int remaining = store.remove(name, oneBasedIndex < 0 ? -1 : oneBasedIndex - 1);
        source.sendSuccess(() -> Component.literal(remaining < 0
                ? "Removed keyframe " + removedNumber + " from '" + name
                        + "'; it is now empty and was deleted."
                : "Removed keyframe " + removedNumber + " from '" + name + "' ("
                        + remaining + " left)."), false);
        warnIfSaveFailed(source, store);
        return 1;
    }
}
