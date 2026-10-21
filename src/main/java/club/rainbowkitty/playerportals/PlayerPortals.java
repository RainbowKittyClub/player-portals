package club.rainbowkitty.playerportals;

import java.util.function.Function;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import club.rainbowkitty.kalic0re.common.item.ItemNames;
import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
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
    // A faint glow, where a nether portal gives 11: every client is shown the portal as a light
    // block of the same level, so its own lighting agrees.
    private static final int PORTAL_LIGHT = 2;

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

    /** The line under {@code playerportals.<key>}, with {@code args} formatted in. */
    public static MutableComponent line(String key, Object... args) {
        return text.of(MOD_ID + "." + key, args);
    }

    /** Tells {@code player} the line under {@code playerportals.<key>}, in chat. */
    public static void say(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(line(key, args));
    }

    /**
     * Registers the item {@code factory} makes as {@code playerportals:<path>}, named in this
     * mod's English by its description id, which a vanilla client has no translation for.
     */
    public static <T extends Item> T registerItem(String path,
            Function<ResourceKey<Item>, T> factory) {
        return ItemRegistration.register(MOD_ID, path, key -> {
            T item = factory.apply(key);
            return ItemNames.named(key, text.of(item.getDescriptionId()), item);
        });
    }

    /** This mod's portals: block, frames and lit registry, from kalic0re's portal framework. */
    public static Portals portals() {
        return portals;
    }

    @Override
    public void onInitialize() {
        text = FallbackText.load(MOD_ID);
        PortalSettings.register();
        CatalystComponents.init();
        FrameBlocks.init();
        PortalMaterials.init();
        PortalSounds.init();
        portals = Portals.register(MOD_ID, "portal", new PlayerPortalKind(),
                PortalBlock.defaultProperties().lightLevel(state -> PORTAL_LIGHT));
        CatalystItems.init();
        PortalTabs.register();
        PortalProtection.register();
        PortalLinks.register();
        CatalystCrafting.register();
        // Required, so singleplayer turns Polymer's own pack on too. A server can still let players
        // in without it (auto-host mod_override false); they see nether stars and crying obsidian.
        PolymerResourcePackUtils.addModAssets(MOD_ID);
        PolymerResourcePackUtils.markAsRequired();
    }
}
