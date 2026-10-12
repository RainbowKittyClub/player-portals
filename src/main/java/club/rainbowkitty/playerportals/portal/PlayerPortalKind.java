package club.rainbowkitty.playerportals.portal;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;

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
