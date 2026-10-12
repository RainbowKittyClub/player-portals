package club.rainbowkitty.playerportals.portal;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

import club.rainbowkitty.playerportals.PlayerPortals;

/** The portal block. It has no item: a portal is lit, never placed. */
public final class PortalBlocks {
    public static final PortalBlock PORTAL = register("portal");

    private PortalBlocks() {
    }

    /** Forces registration; the field above does the work. */
    public static void init() {
    }

    private static PortalBlock register(String path) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, PlayerPortals.id(path));
        return Registry.register(BuiltInRegistries.BLOCK, key,
                new PortalBlock(PortalBlock.properties(key)));
    }
}
