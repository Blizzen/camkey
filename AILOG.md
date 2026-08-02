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
- Corrections/mistakes to record as they happen:
  - (pending: fill in the first time the AI asserts an API that does not
    exist in NeoForge 21.1 / MC 1.21.1 and the compiler or a runtime test
    catches it)
