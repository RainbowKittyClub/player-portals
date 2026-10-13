# Player Portals

Portals that players build from any full, solid block, light in pairs with a Portal Catalyst, and
walk through across dimensions, served to vanilla clients through Polymer. Every API claim below was
checked against `libs/decompiledjava/minecraft-26.2/`, `libs/polymer/` (branch `dev/26.2`) or
`javap` on the 26.2 deobf jar, cited as `File.java:line`. Gameplay claims (what a client renders,
what a bot observes) are unverified until the stage that observes them is done.

S1 (scaffold) is done. S2 (frame geometry) and S3 (block, registry, exit) are the core and build in
that order; S4 (catalysts) makes a portal reachable in play and is where S2 and S3 first become
observable. S5 (permission gates) and S6 (horizontal portals) are independent of each other and
both depend on S4. S7 (bot harness, docs) closes the feature; S4 is the minimum playable build.

---

## Findings that reshape the work

**Vanilla's `Portal` interface does all per-entity work, so the mod has no tick of its own.**
A block implementing `net.minecraft.world.level.block.Portal` calls `entity.setAsInsidePortal(this,
pos)` from `entityInside` (NetherPortalBlock.java:117); the entity's own `handlePortal` then asks
`getPortalDestination` once, when `getPortalTransitionTime` ticks have passed
(Entity.java:2614-2635, PortalProcessor.java:21-28). `handlePortal` returns at once for an entity
with no `portalProcess`, so an idle server pays nothing for any number of lit portals.

**Arriving inside the destination portal is safe even with instant transit.** After a teleport the
entity is on portal cooldown (10 ticks for a player, Player.java:390; 300 for anything else), and
`setAsInsidePortal` on cooldown *refreshes* the cooldown instead of starting a transit
(Entity.java:2601-2603). An entity standing in the far portal therefore stays put until it leaves
and comes back, which is what vanilla relies on too. So the exit is the same place in the far
frame, with velocity and facing kept (`Relative.DELTA` and `Relative.ROTATION`, as
NetherPortalBlock.java:245 passes them), and a player walking in walks out the other side.

**No vanilla block state is both non-colliding and colourable, and none lies flat.** The client
state has to be a vanilla one with no collision, or the vanilla client's own movement prediction
walks into it. Vertical portals get `minecraft:nether_portal[axis=x|z]` itself: correct culling
between neighbours, animated texture, light 11, the purple overlay and ambient sounds the client
adds on its own (NetherPortalBlock.java:227). A flat portal has no such state and arbitrary colour
has none at all, so colour is skipped (see Open decisions) and a flat portal is `light[level=11]`
on the client (invisible, no collision, emits the same light the real block does, so the client's
local light recalculation agrees with the server's) plus one static block display of
`nether_portal[axis=x]` turned flat per interior block. Polymer manages those displays through
`BlockWithElementHolder`: it creates one per block on chunk load behind a palette `maybeHas` check
and on `setBlockState`, destroys it on removal and unload (WorldChunkMixin.java), and never ticks a
holder whose `tickElementHolder` is false (ChunkAttachment.java:89-95).

**Every player crafting path funnels through one static method.** `CraftingMenu.
slotChangedCraftingGrid` (CraftingMenu.java:47-73) serves the crafting table, the inventory grid
(InventoryMenu.java:88) and kalic0re's `PersistentCraftingMenu`; it leaves the result empty when
`ResultContainer.setRecipeUsed(ServerPlayer, RecipeHolder)` answers false, which is also how
`doLimitedCrafting` hides a locked recipe. Wrapping that call is the crafting gate. The crafter
block does not pass a player and goes through `CrafterBlock.getPotentialResults`
(CrafterBlock.java:184), which gets a second, denying wrap. The recipe book is kept in step on
join instead of by an unlock advancement: `awardRecipesByKey` for a player with the node (toasts
once, `ServerRecipeBook.addRecipes` skips known ones, ServerRecipeBook.java:61-73), `resetRecipes`
for one without. An advancement fires once, so a player denied at that moment would never see the
recipe after a later grant.

**Vanilla gives no portal cooldown to riders, so with instant transit a rider who dismounts
inside the far portal goes straight back alone.** Only the entity that crossed gets a cooldown
(Entity.java:2621); its passengers could not use the portal while riding (`canUsePortal(false)`,
Entity.java:3201) and arrive with none. The exit's post-transition step gives every indirect
passenger one; cross-dimension riders are re-mounted before it runs (Entity.java:3084-3088).

**An entity that does not fit the far frame would be planted in its ring.** Vanilla's 2×3
minimum hides this; here a 1-high upright frame is legal for items. So a crossing is refused with
"too small" when the entity's width or height exceeds the far frame on either in-plane axis, and
`isAllowedToEnterPortal`/`canTeleport` (which `handlePortal` checks only after the destination,
silently, Entity.java:2625) are asked up front with their own line.

**A frame block that turns into another block keeps its shape.** Copper weathering, concrete
powder setting and grass dying all set the block in place with neighbour shape updates, which
reach `updateShape` with a neighbour that is still a full cube. The block therefore asks the
registry whether that ring block is still the id the portal was lit with (`ringMatches`, one
index lookup) and fades at once instead of under the next user.

**Commands and fills remove blocks without the removal hook.** `LevelChunk.setBlockState` calls
`affectNeighborsAfterRemoval` only with the neighbour-update flag or a piston
(LevelChunk.java:319-321); `/setblock` and `/fill` set with flag 2. A multi-block portal still
cascades through `destroyBlock`, but a single-block portal or the last block of a fill vanishes
unnoticed. The registry therefore purges a stale record when its frame is lit again, and every
crossing re-validates the far side. Dead partners are cleared on a later tick only when their
chunks are loaded; otherwise they wait in a persisted `pending` list for `ServerChunkEvents.
CHUNK_LOAD`, so no block update ever forces a chunk load in another dimension.

**Pattern keying gave way to a dedicated frame block (user decision, 2026-10-12).** With a
catalyst already choosing which two frames pair, a pattern key bought nothing but mismatch
messages. A frame is now any closed ring of void-infused deepslate (deepslate plus an ender
pearl; crying obsidian to a client without the pack, the same light 10) around an empty
rectangle, any size, any plane, and any two link; the exit turns as a nether portal's does, a
quarter turn between the X and Z planes. `FrameSignature` is gone, `updateShape` asks only
whether an in-plane neighbour is portal or frame block, and feedback goes to chat.

**The portal machinery moved to kalic0re `common/portal` (user decision, 2026-10-12).** Frame
geometry, the portal block, the lit-frame store, the per-block display and the exit math are now
kalic0re 0.27.0's framework (`rkc/mods/kalic0re/docs/plan/portal-framework.md`); this mod keeps
a `PlayerPortalKind` (frame block, material, sounds, where a crossing goes), `PortalLinks` for
destinations, the catalysts and the gates. The look is one material for upright and flat portals
alike: every portal block is an invisible light to the client, and a display per block shows the
nether portal texture tinted `#0a4646` as an item model to a client with the pack
(`scripts/tint_portal_texture.py` makes it from the vanilla texture) and
`nether_portal[axis=x]` as a block display to any other, turned for the Z and flat planes. The
vanilla client's own warp overlay and ambient portal sounds go with the real nether portal
state; the break effect stays a nether portal's.

**Links and lit frames are saved apart, so they are reconciled on start.** The framework keeps
lit frames in its own file and this mod keeps destinations in `playerportals:links`; a crash
between the two saves, or a command clearing blocks behind the removal hook, can leave one
naming what the other lost. `PortalLinks.reconcile` runs at `SERVER_STARTED`: a link from or to
an unlit frame goes, and a lit frame no link reaches fades. Linking also answers false when the
framework refuses to light a frame that another portal lit a moment ago has cut across, and the
catalyst says so instead of being consumed.

**Crossing takes the centre, not the shoulder (user decision, 2026-10-12).** The framework now
takes an entity once the centre of its box is in a portal block or crossed one during the tick,
so brushing a portal's edge no longer sends anyone through. The tint is `#159c9c`, and lit
portals give off the `effect` family in teal (`PlayerPortalKind.emitters`): a thin sheet over
the plane every two ticks, cycling `effect`, `instant_effect` and `entity_effect`, and a pale
spark along the bottom edge every ten.

**The frame block weeps and sounds like deepslate (user request, 2026-10-12).** A client without
the pack sees crying obsidian and gets its purple drips from the client itself; one with the pack
sees the block's own texture on a state lent by Polymer's pool, which neither drips nor, at first,
sounded of stone. So the block schedules a tick for itself when placed (random ticks start blocks
placed before), and a block tries for a drop every 2 to 6 s, sent only to players with the pack.
A pack may re-sprite an existing particle type but never add one, and every vanilla drip type
multiplies a fixed colour (water blue, lava orange, honey amber, obsidian tear purple, nectar
amber, spore blossom green) onto shared grey sprites in client code, so no drip particle reaches
teal. The drop lives an obsidian tear's life (DripParticle.java:386, 541, 598): it hangs on a
random exposed face other than the top 100 ticks, at a spot clear of collision, falls to the
first visual shape straight below it (a fence's post, not its taller collision), at most 16 down,
in the ticks a gravity of 0.01 would take, and lands with a soft dripstone drip, leaving a puddle
until the block's next try at a drop; a drop over nothing, or onto a liquid, is gone when it
would have landed.

How the drop is drawn went through four forms on 2026-10-12 and 13. First a `trail` particle, the
one colourable particle that moves to a target over a set time, as its plain dot (a droplet sprite
was tried and turned down), hanging as a trail that goes nowhere and falling as one that does.
The puddle was then a row of five to eight trail dots, since no particle can be stretched from the
server (only `dust` takes a size, and a uniform one), then an item display of a bar sprite, set
aside because an item is lit and shaded as a model, which a shader client makes plain. The user
then found 26.1's object text component (`Component.object(new AtlasSprite(atlas, sprite))`,
`ObjectContents`): `AtlasGlyphProvider` draws an atlas sprite as an 8-pixel glyph in the text's
colour, so a text display gives vanilla's own `drip_land` sprite what no particle has from the
server, a colour and a scale. The puddle became that, first as a flat sheet along the face, then
as a centre billboard like the tear's own particle (user, 2026-10-13). The trail and the sprite
were lit alike on paper, both 15728880 (`TrailParticle.getLightCoords`, TrailParticle.java:60, and
a brightness override of 15/15), but the user's shader lights entity text its own way and leaves
particles be, and their tints fell on different greys (the trail's `generic_0` is one white pixel,
`drip_land`'s bar grey 213), so the user asked for the drop to be a sprite display too.

As built (2026-10-13), each drop is one text display that changes in place
(`VoidDeepslateBlock.Drop`): vanilla's `drip_hang` sprite on the face, `drip_fall` for the fall,
`drip_land` as the puddle. All three are grey 213 with one white highlight, tinted `#009696` so
the grey renders at 125 of 255, the brightness the user had matched the drop and puddle at. Scale
1 to 2 at random, held through the drop's life, so the dot is 0.05 to 0.1 across and the bar 0.1
to 0.2 long, as in the tear's own 0.2 to 0.4 quad; centre billboard; brightness 15/15; no
background or shadow; pack watchers only. `DisplayRenderer` draws a one-glyph line with the glyph
box from 3 pixels left of the entity to 5 right and 2 above to 10 (its `1 - width/2` and `-height`
offsets at 0.025 a pixel), so each mark's centre lies a pixel right and 9 (hanging dot), 8
(falling dot) or 6.5 (bar) up; the translation, applied after the billboard's turn, moves it onto
the entity whichever way it faces. The dot hangs 0.08 out from the face, so it never cuts into the
block. The fall is one data update and one move: the display's position interpolation (data index
10, `teleport_duration`) set to the fall's ticks makes the client glide it there at constant speed,
as the trail moved (TrailParticle.java:44-56), so the server sends one packet pair per step and
ticks nothing. Only a saved display clamps that to 59 ticks (`Display.java:221`; the synced
value is taken as sent), the client snaps moves over 64 blocks (ClientPacketListener.java:653),
and the longest fall, under 16 blocks, takes 57. The dot comes
to rest 0.01 plus half its height above the floor; landing turns it into the puddle and, with the
interpolation set back to 0 (InterpolationHandler.java:50), snaps it to 0.01 plus half the bar's
thickness. The holder never ticks; each step calls `holder.tick()` once to send its changes,
data before the move. It is a `BlockBoundAttachment` on the frame block (its constructor, which
Polymer marks internal, since the public factory serves only blocks that keep a holder of their
own), so Polymer takes the display away on any change to that block, a strict `/setblock` that
skips `affectNeighborsAfterRemoval` included (WorldChunkMixin.java:87-97); a drop whose display
is gone, with its block or its chunk, is forgotten at that spot's next tick. Drops are kept per
dimension, so frame blocks at the same coordinates in two no longer share one, and forgotten at
server stop. Known: a player who starts watching mid-fall is spawned the dot where it is going
and sees it sit there until it lands, as Polymer spawns an element at its last synced position. The cost over the particles: up to one display per frame
block at a time, and its packets reach every pack player with the chunk loaded rather than those
within 32 blocks, about 0.5 KB/s per nearby player for a 3 by 4 portal.

**Registration reworked (user report, 2026-10-13).** On their client the user saw three faults:
placing the block showed crying obsidian, broken with particles, before the real block; their own
steps and the rain on it sounded of a note block; and it did not light up as crying obsidian does.
All three came from what a client with the pack was sent, compared against `sensible-storage` and
the Enderscape Polymer patch (decompiled):

- The item. A client holding a block item predicts placing that block: it puts the block into its
  own world, plays its place sound and swings, and the server's answer replaces the guess. The
  pack client held crying obsidian (drawn with the block's model), so it guessed crying obsidian
  and showed it until the server's state replaced it. For part of 2026-10-13 it held a `trial_key`
  instead, as Polymer's own `PolymerBlockItem` and the Enderscape patch do: no guess, but the
  server had to swing for it and send it the place sound, and a trial key is what client mods see.
  The user's Sounds mod plays its trial-spawner jingle for any trial key (its built-in
  `sounds/items/trial_keys.json`), so every trial-key Polymer item rings; LambDynamicLights gives
  a trial key no light, nor does Iris give a held non-block item any but by its `item_model`. As
  built, `VoidDeepslateItem` sends the pack client the item of the very state it is shown
  (`infested_deepslate`), with a `block_state` component pinning every property of that state:
  `BlockItem.place` applies the component on the client too (BlockItem.java:70, 118), so the
  client guesses `infested_deepslate[axis=x]`, exactly what the server places, instead of the axis
  of the face clicked. No flash, the client swings and plays deepslate's place sound itself, and
  the server-side workaround is gone. As a block item it is heard as its block: Sounds plays
  infested deepslate's place sound, `block.deepslate.place`, the same it plays for crying
  obsidian. LambDynamicLights would give it its block's light, 0, so the pack carries
  `assets/playerportals/dynamiclights/item/void_infused_deepslate.json`, matching
  `minecraft:infested_deepslate` only with this `item_model` (its `match` is vanilla's
  `ItemPredicate`, components included), at luminance 10, held and dropped. A client without the
  pack still holds crying obsidian, which it is then shown, so its guess is right. With every
  client predicting its own clicks on the block, the block declines Polymer's
  `isPolymerBlockInteraction` (true by default for a Polymer block), which had the server swing
  after any click on it that did something, twice over (vanilla 26.2's `handleUseItemOn` has its
  server-swing branch twice), on top of the client's own swing; the catalysts, held as a heart of
  the sea that predicts nothing, return `SUCCESS_SERVER` when they act, so the server swings for
  them even for the click that uses up the last one.
  `sensible-storage`'s planked crafting tables and bookshelves have the crying-obsidian pattern
  (vanilla block item, note-block pack state); there the flash is a vanilla crafting table or
  bookshelf, which barely shows.
- The state. `PolymerBlockResourceUtils.requestBlock(FULL_BLOCK, …)` hands out note blocks first.
  A client works its own footsteps, hits, placing and falls out from the block it is shown, and so
  do client mods: the user's Presence Footsteps maps `minecraft:note_block` to `woodutility`, and
  coolrain picks rain sounds from lists of block ids, `minecraft:note_block` in its
  `MUSICAL_BLOCKS`. Polymer's full-block pool also holds the infested blocks and the double slabs,
  whose real copies it shows as their host or full block once a state is claimed
  (`DefaultModelData.java:64-82, 533-610`; only claimed states are remapped,
  BlockResourceCreator.java:142-157). `FrameBlocks.textured` asks the pool, through the public
  predicate overload of `BlockResourceCreator.requestBlock` (the pool reached by
  `mixin/PolymerBlockResourceUtilsAccessor`, keeping back the last state as the public path does),
  for `infested_deepslate[axis=x]`, else any full-block state. It sounds as deepslate, Presence
  Footsteps calls it `slate` as it does deepslate, and coolrain lists it nowhere. The cost: a real
  sideways infested deepslate shows as chiseled deepslate (Polymer's chosen host), but infested
  deepslate generates upright (OreFeatures.java:122), so only one a creative player placed
  sideways is touched, and only such a player would predict this state. Cobbled deepslate's double
  slab was tried first and dropped the same day: a pack client predicts that state whenever it
  doubles a real cobbled deepslate slab and would flash this block's texture, the fault this rework
  set out to remove. With the client's own sounds right, the server-wide
  `SoundPatcher.convertIntoServerSound(SoundType.WOOD)` and its `SoundRemapperMixin` are gone; they
  would have left a pack client's own steps on every other mod's note-block blocks, Enderscape's
  stone among them, silent, since the pack muted wood and only converted sounds are replayed. The
  break effect is no longer overridden to deepslate either: each client gets the state it is
  shown, so a pack client sees this block's own texture shatter.
- The light. The pack's state gives no light, and a client relights a block it is shown from that
  block, dimming it on any nearby change. Polymer resends the server's light near a block whose
  client state's light differs (BlockStateMixin.java:30-41, ServerChunkCacheMixin.java:113-123),
  but finds those by asking for the state of a client it knows nothing of, which here is crying
  obsidian, as bright as this; `forceLightUpdates` now tells it so. Known: a creative break the
  server refuses (the break gate, a claim) leaves that pack client dark around the block, since
  its prediction relit the area and the server's light never changed, so nothing is resent until
  the light there next changes. Light sent at the refusal would be undone when the client applies
  the restored block on the server's acknowledgement; a fix needs a resend a tick later.
  Shaders that light by voxel floodfill ignore all of this near the player: the user's Distant
  Voxels takes nearby block light wholly from its own table of light-emitting block ids
  (`lib/lighting/mainLighting.glsl:561`, `lib/misc/voxelization.glsl`, crying obsidian's id 10476
  giving light 26), and lists infested deepslate with plain deepslate. The block can glow there
  only through an entry in the shader pack's `block.properties` for
  `minecraft:infested_deepslate:axis=x`; nothing the server sends reaches it. Without such a
  shader, or with shaders off, the resent vanilla light is what lights the block. The user's copy
  was patched so (2026-10-13; original kept as `_backups/Distant Voxels v0.1.0.zip.orig-20261013`
  in the workspace): `block.10110=infested_deepslate:axis=x`, the other two axes left on 10108,
  so the surface is deepslate's (`terrainMaterials.glsl` sends 10108 to 10111 there; only
  `mat % 4 == 1` means anything, non-solid); `voxelization.glsl` gives 10110 light 29, the soul
  fire colour; `shadowChecks.glsl` lists it as emissive, with the soul torch's colour
  (0.15, 0.85, 0.85) and crying obsidian's brightness (`BRIGHTNESS_CRYING`, 12), both lights being
  10. The held item: Iris gives a block item its block's default state's id when it renders
  (`ItemStackStateLayerMixin.iris$setupId`), here `infested_deepslate[axis=y]`, which is also
  infested deepslate as it generates. That state got its own id, 10111, deepslate in every way, and
  `program/shadow.glsl` treats an item rendering as 10111 as 10110, so the item glows held or
  dropped while generated infested deepslate does not; a real infested deepslate item, creative
  only, glows too. Another voxel shader needs its own entries.
- The break sound (user report, 2026-10-13). Polymer mines its blocks on the server under mining
  fatigue, so a client never finishes first, and sends the breaker the break event (BlockMixin:
  `spawnDestroyParticles`) the client then never plays; it does so for any game mode. A creative
  client breaks at once and plays that event itself, so the breaker heard the block break twice.
  `handleMiningOnServer` now answers false for a creative player, leaving that break to vanilla.
  The answer moved into kalic0re 0.27.0 as `common/mining` `VanillaCreativeBreak` the same day,
  which the frame block and the framework's portal block both implement and `sensible-storage`'s
  planked blocks take too; this mod shades `mining` with `portal`.

**The portal's light (user request, 2026-10-13).** The portal block gives light 2, not a nether
portal's 11 (`PlayerPortals.PORTAL_LIGHT`, passed to `Portals.register` over
`PortalBlock.defaultProperties()`); the framework shows every client the portal as a light block
of the block's own level, so the client stand-in is `light[level=2]` and its lighting agrees.
Portals lit before keep the old light in saved chunks until the light there is next recomputed,
for instance by relighting the portal.

The warp a player hears on arriving is the mod's own
`portal_jump` (the user's recording, trimmed to 1.6 s, high-passed at 220 Hz against its rumble
and faded over its last 0.35 s to mono OGG by ffmpeg; the source stays in `media/sounds/`),
through Polymer's sound-event overlay, so a client without the pack hears vanilla's portal
travel; the framework's `travelSound` hook replaced its fixed vanilla step for this. A static map of each block's
drop tells the three ticks apart and loses nothing worse than one drop's ending over a restart.

**A portal collapses the way a nether portal does.** `updateShape` runs on each neighbour of a
changed block; returning air there when an in-plane neighbour is no longer frame or portal makes
the interior cascade to air (NetherPortalBlock.java:94-108). `affectNeighborsAfterRemoval` is the
removal hook (LevelChunk.java:320, replacing `onRemove`), and is where the registry learns a lit
portal lost a block.

---

## How these stages get tested

Build and lint from the workspace: `scripts/in.sh player-portals ./gradlew build` and
`cd scripts/lint && ./gradlew checkstyleTarget -PtargetDir=../../rkc/mods/player-portals/src/main/java`.
A mixin target is only proven on boot, so every stage past S3 restarts the dev server
(`scripts/devserver.sh rkc/mods/player-portals restart`, port 25624, rcon 25626) and checks
`scripts/devserver.sh rkc/mods/player-portals mixins` reports zero failures.

In-world behaviour is observed by mineflayer bots, `scripts/loadtest/player-portals.js`, built in
S7 but run from S4 on: bots join as 26.1 through ViaFabric + ViaBackwards copied into `run/mods`,
frames are built over RCON, catalysts given over RCON, and a leg passes on what RCON reads back
(`execute if block`) plus what the bot's own client sees (position, dimension, entities). A stage
is not finished until its "Done when" is observed; a failure at stage N implicates stage N.

## S1 — Scaffold — **done**

**Build.** Clone `rkc/mod-template_fabric`, run `setup.sh` (`playerportals`, entry class
`PlayerPortals`), strip the example block, datagen source set and bil keys, set
`kalic0re_version=0.26.0` with `kalic0re_shared_packages=command,text,item,click`, make
`polymer-core`, `polymer-virtual-entity` and `polymer-resource-pack` hard dependencies, blank the
README.

**Done when.** `./gradlew build` produces `build/libs/playerportals-0.1.0.jar` from an entrypoint
that does nothing.

### As built

As planned. Ports came out 25624/25626 from `setup.sh`'s high-water mark. `README.md` is empty on
request; `docs/BUILDING.md` stays.

## S2 — Frame geometry — **done**

**Build.** Package `frame/`, no Minecraft state of its own:

- `PortalPlane`: the three orientations as an enum over `Direction.Axis` with vanilla's meaning for
  X and Z (the horizontal axis the portal runs along, `nether_portal[axis]`) and Y for a flat
  portal. Each gives its `along` axis (X; Z; X), its `up` axis (Y; Y; Z) and its `normal` (Z; X; Y).
- `PortalFrame` record: `dimension`, `origin` (interior corner with the smallest coordinates),
  `plane`, `width` along `along`, `height` along `up`. Iterates its interior and its ring, answers
  `interiorContains`/`ringContains`, lists the chunks its bounding box touches, and has a `Codec`.
  Sizes 1..21 each way; the ring is the full rectangle, corners included, since the pattern is
  defined over it.
- `FrameSignature` record: the ring's block ids in one fixed order, read from the frame corner at
  `origin - along - up`, then `+along` across the bottom, `+up` the far side, `-along` across the
  top, `-up` back down, `2(width + height) + 4` entries. Two frames match when plane, size and
  signature are equal; orientation is world-fixed, so a left-right asymmetric pattern must be built
  the same way round at both ends.
- `FrameFinder`: `find(level, interiorPos, plane)` walks `-along` then `-up` from an interior block
  (air, or this mod's portal block of the same plane) to the corner, measures width and height,
  and accepts only when every interior block is interior and every ring block is frame-grade:
  `state.isCollisionShapeFullBlock(level, pos)` and not air. `findAround(level, framePos,
  clickedFace)` tries the block in front of the clicked face first, then the other five
  neighbours, and for each neighbour direction the two planes whose span contains it, vertical
  planes first; the first valid frame wins.

**Done when.** The attune message of S4 reports the right size and plane for frames built by RCON
in all three orientations, including a frame clicked on its outside face, and refuses a ring with
a slab in it, a 22-wide ring, and a ring around a non-empty interior.

### As built

Superseded in part the same day: the ring is now checked for one block, `FrameBlocks.
VOID_DEEPSLATE`, and `FrameFinder.isLit` answers the lit case in one pass, so `FrameSignature`
and `ringIndex` are gone. The ring leaves out the four corners, as a nether portal's does: a corner touches no
interior block, so no shape update from it ever reaches the portal, and a corner in the pattern
would change the key without the portal noticing until its next crossing. Otherwise as planned,
with `find` taking a `Level` (it needs the dimension key) and `isComplete` a `LevelReader`, so
the block's `updateShape` can reuse the frame-block test. `PortalFrame` grew
`ringIndex`, the inverse of `ring()` order, for the in-place ring check added to S3. Bot legs
observed the attune line for all three orientations ("2×3 east–west", "2×3 north–south",
"3×2 flat"), a click on the side ring block of a flat frame, and refusals for a gold block in a
stone ring (by signature) and a 3×4 against a 2×3 (by shape).

## S3 — Portal block, registry and exit — **done**

**Build.** Package `portal/`:

- `PortalBlock extends Block implements Portal, PolymerBlock, BlockWithElementHolder`, property
  `axis` (X, Y, Z) as the plane; properties as `Blocks.NETHER_PORTAL` (no collision, strength -1,
  light 11, `PushReaction.BLOCK`, glass sound), without random ticks. `getPolymerBlockState`
  answers `nether_portal[axis]` for X and Z, `light[level=11]` for Y. `updateShape` returns air when
  an in-plane neighbour is neither this block with the same axis nor frame-grade.
  `affectNeighborsAfterRemoval` tells the registry. `entityInside` is vanilla's line.
  `getPortalTransitionTime` is 0, `getLocalTransition` is `NONE`. `createElementHolder` is null
  for X and Z and S6's holder for Y. `getCloneItemStack` is empty, as vanilla's.
- `PortalBlocks`: registers `playerportals:portal`, no item.
- `PortalRegistry extends SavedData`, one for the server in the overworld's data storage
  (`SavedDataType`, `DataFixTypes.SAVED_DATA_COMMAND_STORAGE`, as a neutral type with no fixers
  that touch a foreign shape). Holds every lit portal (`PortalFrame` plus its `FrameSignature`) and
  the links as `source -> destination`, a two-way link being two entries. A transient index from
  `(dimension, chunk)` to the portals whose bounding box touches that chunk, rebuilt in the
  constructor, answers `portalAt(level, pos)` and `frameOwner(level, pos)` without a world read.
  Operations: `link(a, b)` (two-way), `linkOneWay(source, destination)`, `remove(portal)` (drops
  every link into or out of it, unlights each partner that is now dead, loads the partner's chunk
  if it must), `light`/`unlight` (set interior blocks with `Block.UPDATE_CLIENTS |
  UPDATE_KNOWN_SHAPE` when lighting, `UPDATE_ALL` when clearing), `destinationOf(portal)`.
  Removal happens before any block is cleared, so the removal hook finds nothing to do for the
  blocks it clears itself.
- `PortalExit`: given the entering entity, its portal and the destination portal, the
  `TeleportTransition`. The entity's position is taken relative to the source frame as vanilla
  does (`PortalShape.getRelativePosition`, PortalShape.java:208-233, generalised to any plane: a
  fraction along `along` and `up` over the room the entity has, and a signed offset across the
  plane from its centre), placed at the same fractions in the destination, nudged out of
  collisions with `PortalShape.findCollisionFreePosition`, and sent with `Relative.DELTA` and
  `Relative.ROTATION`, yaw 0 for the same plane and 90 for X against Z, so a walking player keeps
  walking. Post-transition: `PLAY_PORTAL_SOUND` then `PLACE_PORTAL_TICKET`.
- `getPortalDestination` on the block: look the entry position up; no portal, no destination, a
  destination whose level is gone, whose interior is no longer this block or whose ring no longer
  matches its signature, or an entry portal whose own ring no longer matches — each removes the
  dead portal(s) through the registry, tells a player why in the action bar, and returns null.
  The reads are at most one ring and one interior per crossing, never per tick.
- `PortalEffects`: the sounds and particle bursts for attune, link and collapse.

**Done when.** The dev server boots with zero mixin failures; `setblock` of
`playerportals:portal[axis=x]` shows a vanilla client (or a bot's block registry) a nether portal
block, and a player stepping into it clears it with "This portal leads nowhere".

### As built

Three things changed from the Build text, all from the design review. `updateShape` also asks
the registry whether an in-plane frame neighbour is still the block id the portal was lit with
(`PortalRegistry.ringMatches`), so an in-place swap fades the portal at once: the bot leg that
`setblock`s one ring block to gold saw both ends dark at its first poll. `PortalTravel` refuses a
crossing when the entity does not fit the far frame or `isAllowedToEnterPortal`/`canTeleport`
would veto it, each with its own line, instead of letting `handlePortal` drop the trip silently.
The near side is re-read for completeness per crossing (`FrameFinder.isLit`), since a block
set without neighbour updates would otherwise leave a holed portal usable from its own side.
Later the same day: the fade sound and particles moved from `unlight` to `remove`, played for
every dead portal whose chunks are loaded, so the broken portal itself fades audibly, not only
a partner the registry clears; and `getPolymerBreakEventBlockState` answers a nether portal for
every plane, so a flat portal's interior shatters like glass as an upright one's does, where the
light stand-in broke in silence.
And removal never forces a chunk load: dead partners whose chunks are loaded clear on a following
tick, the rest wait in a persisted `pending` list for `ServerChunkEvents.CHUNK_LOAD`; a stale
record under a frame being lit again is purged first (`forgetStale`), since `/fill` and
`/setblock` remove blocks without the removal hook. `getLocalTransition` is left at its default;
only a client's own block reads it. `PortalExit` also gives every indirect passenger a portal
cooldown after the transition, since vanilla gives one only to the entity that crossed. Travel
legs: A→B in ~600 ms to within 0.00 of the far interior with no bounce back over 1.5 s, B→A after
stepping out and back in, overworld→nether with the dimension change observed by the bot, and a
flat pair where the bot fell through one and landed on the floor under the other.

## S4 — Catalysts — **done**

**Build.** Package `item/`, plus the mod-level `PortalPermissions` and `PortalTabs`:

- `CatalystComponents.ATTUNED`: a `PortalFrame`, registered through
  `PolymerComponent.registerDataComponent` so a vanilla client is never sent it.
- `CatalystItem extends PolymerModelItem` (kalic0re `common/item`): fallback item
  `heart_of_the_sea`, model `playerportals:item/<id>`, stack size 1. `useOn` resolves the frame
  under the click with `FrameFinder.findAround`, then hands to the subclass. `use` while sneaking
  clears the attunement. The attuned frame is shown as a `LORE` line, which a vanilla client reads.
- `PortalCatalystItem`: first click attunes to an unlit valid frame; second click on another unlit
  frame whose plane, size and signature equal the attuned frame's links the two, lights both and
  consumes the item. Mismatches say which of size, plane or blocks differ; a lit frame, the same
  frame, or an attuned frame that has since changed each get their own line.
- `AdminCatalystItem`: never consumed, gated by `playerportals.admin` (fallback op level 2). First
  click sets the destination (lit or unlit; an unlit one is lit as a portal with no outgoing link
  when the first source joins it); each later click on an unlit frame lights it as a one-way
  portal to that destination, any plane or size.
- `CatalystItems`: registration with `ItemRegistration` and `ItemNames` from kalic0re.
- `PortalPermissions`: `playerportals.link`, `playerportals.break`, `playerportals.craft` with
  fallback true (everyone until a permission mod says otherwise); `playerportals.admin` with
  fallback `PermissionLevel.GAMEMASTERS`. All through `CommandPermissions.holds`.
- `PortalTabs`: a Polymer tab with both catalysts, the catalyst in Tools & Utilities and the admin
  one in Operator Utilities (STYLE.md "Items and creative tabs").
- `mixin/HandClicksMixin`: kalic0re `common/click`, so the off hand never answers a click the main
  hand took (a vanilla client sends both: it cannot predict a Polymer item).
- Assets: placeholder 16×16 textures, item definitions, `lang/en_us.json` as the single source of
  English (`FallbackText`), the shaped recipe (echo shards in the corners, ender pearls on the
  edges, a nether star in the middle, one catalyst), hand-written JSON; no unlock advancement, the
  book is filled on join.

**Done when.** Bot legs: attune reports the frame; a second matching frame links (RCON reads
portal blocks in both interiors, the bot's hand is empty); a bot walking into one arrives at the
other, in another dimension when the frames are; a mismatched second frame reports why and links
nothing; the admin catalyst makes two one-way portals to one destination and is not consumed.

### As built

Later the same day, on the user's request: the admin catalyst takes its destination from a
sneaking click on any frame, lit or not, and a plain click with none set says so; a plain click
with one set lights a source as before. The pair catalyst links any two complete unlit frames.
Every line is chat, not the action bar. Otherwise as planned, except the recipe book: no unlock
advancement for the catalyst (the frame block's recipe has an ordinary one). It is reconciled on join
(`CatalystCrafting.register`), awarded to a player with `playerportals.craft` and removed from one
without, because an advancement fires once and a player denied at that moment would never get the
book entry back. That listener sits in a join phase ordered after Fabric's default one: LuckPerms
attaches a player's permission data in its own join listener, registered at `SERVER_STARTING`
and so after this mod's, and a check made before it reads the node as undefined, which the bot
legs caught as a denied player still being awarded the recipe. `HandClicksMixin` is artifacts' mixin with this mod's prefix. Legs observed:
attune, link (both interiors 6/6 portal, hand empty on server and client, the client seeing
`nether_portal`), both mismatch lines, the admin catalyst lighting a 2×3 and a 3×4 source to one
destination and staying in hand, arrival at the destination from a source, and "This portal leads
nowhere" with no move when walking into the destination.

## S5 — Permission gates — **done**

**Build.** `PlayerBlockBreakEvents.BEFORE`: a block that is a lit portal, or on the ring of one,
needs `playerportals.break`, else the break is cancelled with a line in the action bar. The
catalysts' `useOn` ask `playerportals.link` (or `admin`) before anything else.
`mixin/CraftingMenuMixin` wraps `ResultContainer.setRecipeUsed` in `slotChangedCraftingGrid` to
answer false for a catalyst when the player lacks `playerportals.craft`;
`mixin/CrafterBlockMixin` makes `getPotentialResults` empty for a catalyst recipe.

**Done when.** With LuckPerms denying each node to a bot: the bot's crafting-table result slot
stays empty with the recipe laid out; a frame block it breaks is still there after the swing and
the portal still lit; its catalyst click links nothing. With the node granted, each succeeds.

### As built

As planned. With LuckPerms denying `playerportals.break` the creative bot's dig left the stone
and both ends lit; granted, the block went and both interiors were air within ~105 ms. Denying
`playerportals.link` gave "You may not link portals." and left no `attuned` component; unset, the
same click attuned. Crafting: grid filled by window clicks, result slot empty when denied, a heart
of the sea named "Portal Catalyst" when granted. No packet handler threw and no ERROR line was
logged across the run.

## S6 — Horizontal portals — **done**

**Build.** `display/FlatPortalHolder`: an `ElementHolder` with one `BlockDisplayElement` of
`nether_portal[axis=x]` rotated a quarter turn about X so its two faces lie flat at the block's
mid-height, no tick. `PortalBlock.createElementHolder` returns one for `axis=y`. `FrameFinder`,
`PortalFrame` and `PortalExit` already take the Y plane; this stage is the visual and its legs.

**Done when.** A flat frame links to another flat frame; a bot sees one `block_display` entity per
interior block over each; a bot that walks off the frame into the hole arrives under the far
portal, and one that climbs into a ceiling portal comes out above the far one.

### As built

As planned. The bot saw exactly one `block_display` at each interior block centre of each flat
portal and `light` as the block, walked off the ring into one 2×2 flat portal and came out under
the other onto the floor below. The ceiling entry is not covered by a leg.

## S7 — Bot harness and records — **done**

**Build.** `scripts/loadtest/player-portals.js` after `better-barriers.js`: RCON-built rigs on a
forceloaded platform, one leg per "Done when" line above, LuckPerms legs skipped when its jar is
absent. Add the mod to `scripts/lint/build.gradle`'s `mods` map and a row to the workspace
`CLAUDE.md`. First commit and push.

**Done when.** `node scripts/loadtest/player-portals.js` reports every leg PASS against a fresh
dev server, and `lintAll` is clean.

### As built

`scripts/loadtest/player-portals.js`, 70 legs after the frame-block and framework reworks, all
PASS against a fresh dev server (2026-10-13), among them the drop legs (per drop one
`text_display` through `drip_hang`, `drip_fall` and `drip_land` in `#009696`, same entity id,
scale 1 to 2, centre billboard, brightness 15728880, background 0, the glide's data before its
move, the dot 0.01 plus half its height over the floor and the puddle 0.01 plus half the bar's,
the drip sound under it; a drop over nothing gone at its landing tick; fence, water and slab
floors; a strict `/setblock` taking the display; none for a bot without the pack, never more at
once than frame blocks standing) and the frame-block legs (`infested_deepslate[axis=x]` with the
pack, crying obsidian without; a `trial_key` item with the pack; one place sound and a server
swing to a placer with the pack, two swing packets in one tick being vanilla's
`handleUseItemOn`, which has its server-swing branch twice; no swing or sound when the item opens
a door; light resent within 150 ms of placing and of breaking beside it; a creative breaker sent no break event and a survival one exactly one, each watcher one, with and without the pack; the frame item held as `infested_deepslate` pinned to `axis=x` with the pack, its placement predicted, so the placer is sent no sound or swing in any case and a watcher one of each; a catalyst's attune, link (the last of a stack) and admin clicks swinging from the server, its refusals not; portal stand-ins `light[level=2]`, and a creative break of a portal's interior sending the breaker no break event for it; wood sounds under their
vanilla ids), one `item_display` per interior
block for a bot with the pack and one `nether_portal` block display for a bot that declined it, the quarter turn between an east–west and a
north–south frame, the sneak-click destination, a shared ring column breaking both portals with
the beacon sound heard, and a flat portal's interior shattering as nether portal blocks. The lint map and the workspace `CLAUDE.md` row are in; `lintAll` reports nothing for this
mod, its 335 findings all sit in `rk-tweaks` from before. Not covered by a leg: the "too
small" refusal, the rider cooldown, and a dead partner clearing when its unloaded chunk loads
(both ends are forceloaded in every leg). No remote exists yet for a push.

## Open decisions

- **Colour.** Skipped: every colourable vanilla state collides, and a translucent custom model
  needs the resource pack, which would split rendering into a pack path and a vanilla path. What
  would revive it: a pack-only `ItemDisplay` of a dyed flat model per block, shown to pack clients
  instead of the block state, with vanilla clients keeping the nether portal look.
- **Arrival overlay.** Moot since the move to display-drawn portals: the client's block is a
  light, so no warp overlay, trigger sound or closed screen; the trip plays vanilla's portal
  travel sound through `PLAY_PORTAL_SOUND`. Ambient portal particles and sound would need the
  server to send them, the mod's first per-tick cost, and are not sent.
- **Smallest frame.** 1×1 is allowed (items and small mobs fit); raise the minimum if players find
  stray 1×1 portals confusing.

## Reference: verified signatures

- `Portal`: `getPortalTransitionTime(ServerLevel, Entity)` default 0;
  `getPortalDestination(ServerLevel, Entity, BlockPos)` nullable; `getLocalTransition()`
  (Portal.java:9-20).
- `TeleportTransition(ServerLevel, Vec3, Vec3, float, float, Set<Relative>, PostTeleportTransition)`
  (TeleportTransition.java:43-51); `PLAY_PORTAL_SOUND`, `PLACE_PORTAL_TICKET`, `then`.
- `Relative.DELTA`, `Relative.ROTATION`, `Relative.union(Set...)` (Relative.java:21-28);
  `ROTATE_DELTA` turns the kept velocity with the yaw change (PositionMoveRotation.java:50-55).
- `BlockBehaviour.updateShape(BlockState, LevelReader, ScheduledTickAccess, BlockPos, Direction,
  BlockPos, BlockState, RandomSource)` (BlockBehaviour.java:147);
  `affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean)` (:172);
  `entityInside(BlockState, Level, BlockPos, Entity, InsideBlockEffectApplier, boolean)` (:377).
- `BlockState.isCollisionShapeFullBlock(BlockGetter, BlockPos)` (BlockBehaviour.java:889), cached.
- `SavedDataType(Identifier, Supplier<T>, Codec<T>, DataFixTypes)`; `SavedDataStorage.
  computeIfAbsent(SavedDataType)`; `ServerLevel.getDataStorage()` (ServerLevel.java:1475).
- `PolymerBlock.getPolymerBlockState(BlockState, PacketContext)`; `BlockWithElementHolder.
  createElementHolder(ServerLevel, BlockPos, BlockState)` nullable, `tickElementHolder` default
  false.
- `CommandPermissions.holds(ServerPlayer, String, PermissionLevel)` and `holds(ServerPlayer,
  String, boolean)` (kalic0re 0.26.0); `FallbackText.load(modId)`, `.of(key, args...)`;
  `PolymerModelItem(Item fallback, Identifier model, Properties)`; `ItemRegistration.register(ns,
  path, key -> item)`; `ItemNames.named(key, Component, item)`.
- `PlayerBlockBreakEvents.Before.beforeBlockBreak(Level, Player, BlockPos, BlockState,
  BlockEntity)` returns false to cancel (fabric-events-interaction-v0 5.3.6).
- `UseOnContext.getClickedPos()`, `getClickedFace()`, `getPlayer()`, `getHand()`,
  `getItemInHand()`, `isSecondaryUseActive()`.
