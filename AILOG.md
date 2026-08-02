# AI usage log

Running notes on how AI (Claude, via Claude Code) was used while building
CamKey, kept live during development. Feeds the AI-usage section of the
README. Newest entries at the bottom.

## Session 1: plan + spike

- Used AI as a design interlocutor before writing any code: walked the whole
  decision tree (camera mechanism, client vs server commands, data model,
  persistence format and location, interpolation timing, failure policy,
  scope cuts) as a structured Q&A, choosing between explicit trade-offs.
- Key decisions made in that session: invisible client-side dolly entity as
  the camera rig (instead of teleporting the player or mixin-ing the camera),
  client-side Brigadier commands, Mojang Codecs to JSON persisted in the world
  folder, distance-weighted segment timing with a single global ease.
- AI scaffolded the repo from the official NeoForge MDK template and wrote a
  throwaway spike (orbit-the-player camera flight) to prove the riskiest
  mechanism first, before any of the real feature was built.
- Spike verdict: compiled first try, verified in-game (smooth orbit, clean
  camera handback, interrupt-and-restart worked).

## Session 1 continued: full system

- AI wrote the phase-2 system (Keyframe/Sequence codecs, SequenceStore,
  CameraPath, PlaybackController, command tree, client config) against the
  architecture locked in the planning Q&A. Compiled first try.
- Corrections/mistakes recorded:
  - The first draft of SequenceStore made the corrupt-file recovery flag
    write-once (final field with a plain getter). Every command fetches the
    store, so the "could not read your file, started fresh" warning would
    have printed on every command for the rest of the session. Caught while
    writing the command layer against the store's API, before the phase was
    ever built or committed; reworked into a consume-once notice
    (consumeRecoveryNotice()).
  - Notably, zero NeoForge/Minecraft API hallucinations across the whole
    project: both phases compiled on the first attempt. The insurance
    against that class of error (spike the camera rig first, verify
    in-game before building on it) turned out not to be needed, but it was
    the right insurance to buy.
- Full-system verdict: verified in-game (capture, eased playback, stop,
  interrupt-and-restart, remove/undo, tab completion, error paths,
  persistence across world reload).

## Session 2: adversarial pass and fix batch

- Ran an adversarial review of the finished repo against the assessment
  document: cold-cloned it from GitHub and built it like a reviewer would,
  swept the committed file list, and attacked the code rubric row by row.
- The pass found two behavioral bugs the happy-path testing missed (the
  playback clock keeps running while the game is paused; playback goes
  zombie if the dimension changes mid-flight), one reviewer-facing trap
  (the brief's own example "/camkey play intro 10 seconds" was a parse
  error because of the trailing word), and one silent-failure gap (a
  failed disk write only logged). All four filed as issues with symptom,
  cause, proposed change, and a test, then fixed in one batch.
- Worth noting for the AI-fluency question: both behavioral bugs were
  found by the AI auditing its own earlier output at a different
  altitude (adversarial review vs. construction), which is the working
  pattern this project used throughout: build, then attack the build.
