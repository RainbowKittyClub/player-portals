package club.rainbowkitty.playerportals.portal;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SpellParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.kalic0re.common.portal.PortalEmitter;
import club.rainbowkitty.kalic0re.common.portal.PortalExit;
import club.rainbowkitty.kalic0re.common.portal.PortalFrame;
import club.rainbowkitty.kalic0re.common.portal.PortalKind;
import club.rainbowkitty.kalic0re.common.portal.PortalMaterial;
import club.rainbowkitty.kalic0re.common.portal.Portals;
import club.rainbowkitty.playerportals.PlayerPortals;
import club.rainbowkitty.playerportals.frame.FrameBlocks;

/**
 * This mod's kind of portal: framed in void-infused deepslate, drawn as the tinted portal
 * material, leading wherever {@link PortalLinks} says. Each crossing re-reads the far end, never
 * more than once per entry, and tells a player in chat why it did not go.
 */
public final class PlayerPortalKind implements PortalKind {
    private static final BlockState PLAIN = Blocks.NETHER_PORTAL.defaultBlockState()
            .setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
    // The material's tint and two shades of it, opaque, as the effect particles take colours.
    private static final int TEAL = 0xFF159C9C;
    private static final int PALE_TEAL = 0xFF5FDEDE;
    private static final int DEEP_TEAL = 0xFF0A4646;
    private static final float WARP_VOLUME = 0.6F;

    private @Nullable PortalMaterial material;

    @Override
    public boolean isFrameBlock(BlockState state) {
        return state.is(FrameBlocks.VOID_DEEPSLATE);
    }

    // Built on first use: the item registers after this kind is handed to the framework.
    @Override
    public PortalMaterial material() {
        if (material == null) {
            material = new PortalMaterial(new ItemStack(PortalMaterials.MATERIAL), PLAIN);
        }
        return material;
    }

    // A thin sheet of the effect family in turn over the whole plane, and a slow drift of pale
    // sparks along the bottom edge.
    @Override
    public List<PortalEmitter> emitters() {
        List<ParticleOptions> series = List.of(
                SpellParticleOption.create(ParticleTypes.EFFECT, TEAL, 1.0F),
                SpellParticleOption.create(ParticleTypes.INSTANT_EFFECT, PALE_TEAL, 1.0F),
                ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, DEEP_TEAL));
        return List.of(PortalEmitter.sheet(series, 0.1, 2, 0.0F, 2),
                new PortalEmitter(List.of(SpellParticleOption.create(ParticleTypes.EFFECT,
                        PALE_TEAL, 1.0F)), new Vec3(0.0, -1.0, 0.0), new Vec3(0.5, 0.05, 0.05),
                        true, 1, 0.02F, 10));
    }

    // The warp, at the player's own ear: the pack's sound, or vanilla's portal travel without it,
    // which Polymer swaps in per player.
    @Override
    public void travelSound(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(PortalSounds.PORTAL_JUMP),
                    SoundSource.AMBIENT, player.getX(), player.getY(), player.getZ(), WARP_VOLUME,
                    1.0F, player.getRandom().nextLong()));
        }
    }

    @Override
    public void onLit(ServerLevel level, PortalFrame frame) {
        PortalEffects.lit(level, frame);
    }

    @Override
    public void onFaded(MinecraftServer server, @Nullable ServerLevel level, PortalFrame frame) {
        PortalLinks.of(server).unlink(server, frame);
        if (level != null && level.isLoaded(frame.origin())) {
            PortalEffects.faded(level, frame);
        }
    }

    @Override
    public @Nullable TeleportTransition onEnter(ServerLevel level, Entity entity,
            PortalFrame frame, BlockPos entryPos) {
        MinecraftServer server = level.getServer();
        Portals portals = PlayerPortals.portals();
        PortalLinks links = PortalLinks.of(server);
        PortalFrame to = links.destinationOf(frame);
        if (to == null) {
            return refuse(entity, "portal.unlinked");
        }
        ServerLevel toLevel = server.getLevel(to.dimension());
        if (toLevel == null || !portals.has(server, to) || !portals.finder().isLit(toLevel, to)) {
            // A far end gone without the framework hearing of it: drop it and what led there.
            if (portals.has(server, to)) {
                portals.fade(server, to);
            } else {
                links.unlink(server, to);
            }
            return refuse(entity, "portal.broken");
        }
        if (!PortalExit.fits(entity.getDimensions(entity.getPose()), to)) {
            return refuse(entity, "portal.too_small");
        }
        // Vanilla asks these after the destination and then drops the trip without a word.
        if (!level.isAllowedToEnterPortal(toLevel) || !entity.canTeleport(level, toLevel)) {
            return refuse(entity, "portal.cannot_travel");
        }
        return portals.transition(entity, frame, toLevel, to);
    }

    private static @Nullable TeleportTransition refuse(Entity entity, String key) {
        if (entity instanceof ServerPlayer player) {
            player.sendSystemMessage(PlayerPortals.text().of(PlayerPortals.MOD_ID + "." + key));
        }
        return null;
    }
}
