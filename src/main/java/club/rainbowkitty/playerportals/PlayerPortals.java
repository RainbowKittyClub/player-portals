package club.rainbowkitty.playerportals;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import club.rainbowkitty.kalic0re.common.text.FallbackText;
import club.rainbowkitty.playerportals.frame.FrameBlocks;
import club.rainbowkitty.playerportals.item.CatalystComponents;
import club.rainbowkitty.playerportals.item.CatalystCrafting;
import club.rainbowkitty.playerportals.item.CatalystItems;
import club.rainbowkitty.playerportals.portal.PortalBlocks;
import club.rainbowkitty.playerportals.portal.PortalProtection;
import club.rainbowkitty.playerportals.portal.PortalRegistry;

/** Mod entrypoint. */
public final class PlayerPortals implements ModInitializer {
    public static final String MOD_ID = "playerportals";

    private static FallbackText text;

    /** {@code playerportals:<path>} identifier. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** This mod's English, as translatable components a vanilla client can read. */
    public static FallbackText text() {
        return text;
    }

    @Override
    public void onInitialize() {
        text = FallbackText.load(MOD_ID);
        CatalystComponents.init();
        FrameBlocks.init();
        PortalBlocks.init();
        CatalystItems.init();
        PortalTabs.register();
        PortalProtection.register();
        PortalRegistry.register();
        CatalystCrafting.register();
        // Not markAsRequired(): without the pack a catalyst shows as a heart of the sea and the
        // frame block as crying obsidian.
        PolymerResourcePackUtils.addModAssets(MOD_ID);
    }
}
