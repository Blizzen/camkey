# AI usage log

Running notes on how AI (Claude, via Claude Code) was used while building
CamKey, kept live during development. The collaboration followed the skill
flows from [mattpocock/skills](https://github.com/mattpocock/skills):
sharpen by interview (`/grill-me`), settle the risky unknown with throwaway
code (`/prototype`), implement, then attack the result and turn the
findings into tickets. Newest entries at the bottom of each section.

## Grilling: the decision-tree interview

- Before any code, the AI walked the whole design decision tree one
  question at a time, each with a recommended answer: camera mechanism,
  command side, the spec's add/play naming ambiguity, keyframe data,
  serialization, file location, motion shape, retake rule, scope line,
  repo home. Facts were looked up; decisions were put to the human. No
  code until shared understanding.
- Decisions locked there: invisible client-side dolly entity as the camera
  rig (instead of teleporting the player or overriding the camera
  transform), client-side Brigadier commands, Codecs to JSON persisted in
  the world folder, distance-weighted constant speed with one global ease,
  interrupt-and-restart playback.

## Prototype: the camera-rig spike

- The one mechanism neither of us had personally shipped (point the game
  camera at a client-spawned entity and fly it along a path) was a genuine
  design question, so it got throwaway code first: a hardcoded orbit
  flight around the player, built before any feature work.
- Verdict, verified in-game: frame-smooth motion from tick-rate updates,
  clean camera handback, interruption works. The answer was kept, the
  spike deleted; it survives only in git history.

## Implement: build phases

- The AI drafted each layer against the locked decisions (data model,
  store, path math, controller, commands, config). Every phase compiled
  first try and was verified in-game before the next phase began.
- Grilling, prototype, and implementation ran in one unbroken context
  window, so the build worked from the same thinking as the interview.

## Catches

- The first draft of SequenceStore made the corrupt-file recovery flag
  write-once (final field with a plain getter). Every command fetches the
  store, so the "could not read your file, started fresh" warning would
  have printed on every command for the rest of the session. Caught at the
  seam, while writing the command layer against the store's interface and
  before the phase was ever committed; reworked so the warning shows
  exactly once.
- Notably, zero NeoForge/Minecraft API hallucinations across the whole
  project: every phase compiled on the first attempt. The insurance
  against that class of error (prototype the camera rig first, verify
  in-game before building on it) turned out not to be needed, but it was
  the right insurance to buy.

## Adversarial review: findings as tickets

- With the build "done", the repo was reviewed against the assessment
  document as its spec: cold-cloned from GitHub and built the way a
  reviewer would, committed file list swept, code attacked rubric row by
  row.
- Findings: the playback clock kept running while the game was paused;
  playback went zombie if the dimension changed mid-flight; the brief's
  own example "/camkey play intro 10 seconds" was a parse error; a failed
  disk write only logged. Each became an issue (#1 to #4) with symptom,
  cause, proposed change, and a test, plus a verification ticket (#5)
  blocked by the fixes.
- Worth noting for the AI-fluency question: both behavioral bugs were
  found by the AI auditing its own earlier output at a different altitude
  (review vs. construction). Build, then attack the build.

## Verification pass (closing #5)

- All in-game: pause-freeze confirmed (with the vanilla caveat that a
  LAN-opened world no longer pauses at all, documented in the README);
  mid-flight dimension change ends playback cleanly with a chat message;
  the spec's exact "10 seconds" syntax parses; middle-index remove, config
  screen, and dimension refusals all behave; a violent client kill
  mid-playback lost zero data; a hand-corrupted save file was kept as
  .bak with a one-time notice; and a deliberately locked save file
  produced the in-chat disk warning, with the next capture flushing the
  stranded data to disk. Fixes shipped as v0.1.1; issues closed with
  dated comments.
