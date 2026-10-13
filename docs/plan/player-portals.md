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
sees a note block, which neither drips nor sounds of stone. So the block schedules a tick for
itself when placed (random ticks start blocks placed before), and each tick sends players with the
pack within 32 blocks one `trail` particle in `#008888` falling from a random exposed face, then
reschedules 5 to 15 s on, about crying obsidian's pace and nothing between drops. The break
effect names deepslate (`getPolymerBreakEventBlockState`), which Polymer sends to the miner too;
step, hit, place and fall go through Polymer's sound patcher, `SoundPatcher.convertIntoServerSound
(SoundType.WOOD)`: the pack mutes the client's own wood sounds and the server plays the real
block's, deepslate here, to the player as well. That conversion is server-wide: every wood-sounding
block's sounds now reach a pack client from the server instead of its own prediction. The patcher
renames the moved sound ids in every outgoing packet whether or not the receiver holds the pack,
which would silence every wood sound a client without it hears from others, so
`mixin/SoundRemapperMixin` keeps the rename to clients with the pack. Later the same day, on
seeing few drops: a block tries every 2 to 6 s, each drop is a bead of dust clinging to the face
plus the falling trail, and the block's next tick, 25 ticks on, lands it with a soft dripstone
drip for the same players. A pack may re-sprite an existing particle type but never add one,
and every vanilla drip type multiplies a fixed colour (water blue, lava orange, honey amber,
obsidian tear purple, nectar amber, spore blossom green) onto shared grey sprites in client code,
so none reaches teal. The drop is therefore vanilla's `trail`, the one colourable particle that
moves to a target over a set time, kept as its plain dot after a droplet sprite was tried and
turned down, and living an obsidian tear's life (DripParticle.java:386, 541, 598): it hangs on
the face 100 ticks as a trail that goes nowhere, falls as one that does to the first block with
collision below, at most 16 down, in the ticks a gravity of 0.01 would take, and lands as a
the drip; a drop over nothing fades in the air. The drop hangs 0.08 out from the face and rests
0.14 above the floor, being a quarter-block quad that faces the camera, which the floor would
otherwise swallow half of. The puddle it leaves is vanilla's own landing sprite,
`drip_land` of the `particles` atlas, tinted `#008888` as the one glyph of a text display. 26.1's
object text component (`Component.object(new AtlasSprite(atlas, sprite))`, `ObjectContents`)
puts an atlas sprite in text, `AtlasGlyphProvider` draws it as an 8-pixel glyph in the text's
colour, and the display gives it what no particle has from the server: a fixed orientation
(billboard fixed, pitched flat, yawed along the face the drop hung from) and a scale, 1 to 2 at
random, so the bar, four pixels of the eight, is 0.1 to 0.2 block long, as in the tear's own 0.2
to 0.4 quad. `DisplayRenderer` draws a one-glyph line with the glyph box from 3 pixels left of the
entity to 5 right and 2 above to 10 (its `1 - width/2` and `-height` offsets at 0.025 a pixel,
the glyph's top one pixel over its baseline), and the bar is the box's fourth row, so the
translation moves the bar's centre, a pixel right and 6.5 up, onto the entity, which sits 0.01
above the floor with the bar's near edge 0.01 off the face. Full bright, no background, no shadow,
pack watchers only (visibility predicate), one non-ticking `ChunkAttachment` holder per puddle,
dried when the block's next drop forms 2 to 6 s on, as a tear's splash lasts, or the block goes.
Before this (same day) the puddle was a row of five to eight trail dots a twentieth of a block
apart, each facing the camera along a line fixed in the world, because no particle can be
stretched from the server (only `dust` takes a size, and a uniform one); an item display of a bar
sprite was tried before that and set aside as an item is lit and shaded as a model, which a shader
client makes plain. The user found the sprite component and that it draws as wanted on their
client. The warp a player hears on arriving is the mod's own
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

`scripts/loadtest/player-portals.js`, 51 legs after the frame-block and framework reworks, all
PASS in about 2 min 30 s against a fresh dev server, among them one `item_display` per interior
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
