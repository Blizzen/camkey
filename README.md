# CamKey

A camera keyframe and playback tool for Minecraft 1.21.1 (NeoForge).

You stand where you want the camera, save that spot, move to the next spot,
save again, and then play the whole thing back as one smooth camera move. It
is the kind of tool a Production Associate would use to set up a cinematic
shot for a recording session, without touching the actor or the set.

## For producers: what it does

- Your view flies the path; your character stays exactly where they were and
  is visible in the shot. Nothing about the world or the actor is changed.
- The camera glides through middle keyframes without stopping, moves at
  constant speed on long legs, and eases in and out at the ends, like a real
  dolly move.
- Shots are saved the moment you capture them. A crash cannot lose them.
- Shots are saved inside the world folder, so if you zip a world and hand it
  to someone else, the shots travel with it.

### Commands

| Command | What it does |
| --- | --- |
| `/camkey add <name>` | Saves your current position and view angle as the next keyframe of sequence `<name>` (creates it if new) |
| `/camkey play <name> [seconds]` | Plays the sequence over that many seconds (default 10, configurable) |
| `/camkey stop` | Ends playback immediately and returns the camera |
| `/camkey list` | Shows every sequence in this world with its keyframe count |
| `/camkey remove <name> [number]` | Deletes a keyframe (the most recent one if no number given), so a fumbled capture is one command to undo |

A typical session: stand at the establishing shot, `/camkey add intro`, fly to
the reveal, `/camkey add intro`, fly to the close-up, `/camkey add intro`,
then `/camkey play intro 12`. Bad take? `/camkey play intro 12` again, it
restarts instantly.

If something is wrong (a name that does not exist, a sequence with only one
keyframe, a sequence recorded in a different dimension), you get a plain chat
message saying exactly what and why. Nothing crashes, and the camera always
comes back to the player: on finish, on `/camkey stop`, and even if you leave
the world mid-playback.

## Build and run

Requires Java 21. From the repo root:

```
./gradlew build        # jar lands in build/libs/
./gradlew runClient    # launches a dev client with the mod loaded
```

To use the jar in a normal install: NeoForge 21.1.x for Minecraft 1.21.1,
drop `camkey-<version>.jar` in the `mods` folder.

Config (default playback seconds, easing on/off) is in
`config/camkey-client.toml`, also editable in-game via Mods > CamKey > Config.

## Architecture

Four small layers, each in its own package, talking in one direction:

- **`camera`** - the data model. `Keyframe` (position + view angles) and
  `Sequence` (ordered keyframes, recorded dimension) are immutable records
  with Mojang Codecs, so (de)serialization is declarative and versionable.
  `DollyCameraEntity` also lives here: an invisible, physicsless entity that
  the game camera rides during playback.
- **`store`** - persistence. One JSON file per world
  (`<world>/camkey/sequences.json`), written atomically on every mutation
  (temp file, then rename). The file carries a `schemaVersion` so fields like
  roll, FOV, or per-keyframe hold times can be added later without breaking
  old files. An unreadable file is never deleted: it is kept as `.bak`, the
  library starts fresh, and the user is told once in chat.
- **`playback`** - the math and the state machine, separately. `CameraPath`
  is pure geometry: progress in, camera pose out, with distance-weighted
  timing (constant speed) and shortest-path yaw interpolation (350° to 10°
  turns 20° through north, not 340° backwards). `PlaybackController` owns
  time, the dolly entity, and the camera handoff.
- **`command`** - the Brigadier tree, all validation, and every user-facing
  message. The store and controller only ever see valid input.

### Why a camera entity instead of moving the player?

The game camera is pointed at the dolly entity using the same mechanism
spectator mode uses, all public API, no mixins. The renderer interpolates
entity positions between ticks natively, so the mod updates the dolly 20
times a second and gets frame-smooth motion at any FPS for free. The player
is never moved, so there is no physics, collision, or "actor ended up inside
a wall" failure mode, and cleanup is a single "point the camera back at the
player" call that runs on finish, stop, interruption, and world exit.

### Why client-side commands?

The whole feature is presentation: nothing about it needs server authority,
and the spec scopes multiplayer sync out. Registering the Brigadier tree
through NeoForge's client command event keeps everything on the client
thread, no networking, no thread handoffs. On a singleplayer world the
integrated server is local, which is how the store reaches the world folder.

## Assumptions made (spec ambiguities)

- The spec says `/camkey add intro` captures "a named keyframe" but plays
  "a named sequence". Read here as: the name refers to the **sequence**, and
  `add` appends an anonymous keyframe to it. That matches the play command's
  noun and makes the capture workflow one repeated command.
- "10 seconds" in the play example is the **total** duration of the move,
  split across segments by distance.
- A sequence stays in the dimension it was recorded in; playing or extending
  it from another dimension is refused with a message rather than guessed at.
- Playing while something is already playing **replaces** the running
  playback. On a recording day, "bad take, go again" should be one command,
  not two.

## Known limitations / what I would do differently

- Corners: the path is piecewise linear, so direction changes at keyframes
  are visible on sharp angles. A Catmull-Rom spline through the keyframes is
  the natural next step and slots into `CameraPath` without touching
  anything else.
- The camera only renders chunks the client has loaded, so a path far from
  the player can show unloaded terrain.
- Sequence names are single words (Brigadier word argument).
- Multiplayer: works when connected to a server (sequences save under
  `config/camkey/by-server/`), but playback near other players is untested
  and out of scope per the spec.
- `CameraPath` is deliberately pure math and would be the first thing to get
  unit tests; wiring a JUnit setup into the mod toolchain did not make the
  time box.

## If I had another week

- **Path preview**: render the camera path in-world as a line before playing
  it. The single most useful production feature after playback itself.
- Catmull-Rom splines, per-keyframe hold times, and roll/FOV keyframing (the
  file format already has room for them behind `schemaVersion`).
- A capture keybind with a "current working sequence" so an operator can
  place keyframes without opening chat.
- Multiple simultaneous cameras: the dolly/controller split was designed so
  a second dolly is not a rewrite.

## AI usage notes

Built with Claude (Claude Code) as the pair; a running log kept during
development is in [AILOG.md](AILOG.md).

- **AI-assisted**: the design interview before any code (every major decision
  above was chosen from explicit trade-offs in that session), scaffolding
  from the official NeoForge MDK template, and first drafts of each layer
  against the locked architecture.
- **By hand / human judgment**: the decisions themselves, in-game
  verification of every phase (smoothness, camera handback, interrupt
  behavior, persistence across reload), and review of each draft before it
  was committed.
- **A catch**: the first draft of the persistence layer's corrupt-file
  recovery marked its "recovered" flag as write-once, which would have
  repeated the "started fresh" warning on every single command for the rest
  of the session. Caught while reviewing the command layer against the store,
  and reworked into a consume-once notice. The build that risks that kind of
  bug never reached a commit, which is exactly what the review step is for.
- The riskiest mechanism (the camera-entity rig) was spiked and verified
  in-game first, before any feature code, precisely because AI assertions
  about engine APIs are the least trustworthy part of AI-assisted modding;
  this one held up.
