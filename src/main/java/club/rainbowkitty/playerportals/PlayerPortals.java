package club.rainbowkitty.playerportals;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.soundpatcher.api.SoundPatcher;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SoundType;

import club.rainbowkitty.kalic0re.common.portal.PortalBlock;
import club.rainbowkitty.kalic0re.common.portal.Portals;
import club.rainbowkitty.kalic0re.common.text.FallbackText;
import club.rainbowkitty.playerportals.frame.FrameBlocks;
import club.rainbowkitty.playerportals.item.CatalystComponents;
import club.rainbowkitty.playerportals.item.CatalystCrafting;
import club.rainbowkitty.playerportals.item.CatalystItems;
import club.rainbowkitty.playerportals.portal.PlayerPortalKind;
import club.rainbowkitty.playerportals.portal.PortalLinks;
import club.rainbowkitty.playerportals.portal.PortalMaterials;
import club.rainbowkitty.playerportals.portal.PortalProtection;
import club.rainbowkitty.playerportals.portal.PortalSounds;

/** Mod entrypoint. */
public final class PlayerPortals implements ModInitializer {
    public static final String MOD_ID = "playerportals";

    private static FallbackText text;
    private static Portals portals;

    /** {@code playerportals:<path>} identifier. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** This mod's English, as translatable components a vanilla client can read. */
    public static FallbackText text() {
        return text;
    }

    /** This mod's portals: block, frames and lit registry, from kalic0re's portal framework. */
    public static Portals portals() {
        return portals;
    }

    @Override
    public void onInitialize() {
        text = FallbackText.load(MOD_ID);
        CatalystComponents.init();
        FrameBlocks.init();
        PortalMaterials.init();
        PortalSounds.init();
        portals = Portals.register(MOD_ID, "portal", new PlayerPortalKind(),
                PortalBlock.defaultProperties());
        CatalystItems.init();
        PortalTabs.register();
        PortalProtection.register();
        PortalLinks.register();
        CatalystCrafting.register();
        // Not markAsRequired(): without the pack a catalyst shows as a heart of the sea, the
        // frame block as crying obsidian and the portal as a nether portal.
        PolymerResourcePackUtils.addModAssets(MOD_ID);
        // The frame block's pool stand-in is a note block, whose step, hit, place and fall sounds
        // a client with the pack would make itself, in wood. Served from the server instead, those
        // sounds come from the real block: deepslate. Every wood-sounding block pays the same way.
        SoundPatcher.convertIntoServerSound(SoundType.WOOD);
    }
}
