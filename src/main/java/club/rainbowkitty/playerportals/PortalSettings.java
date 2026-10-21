package club.rainbowkitty.playerportals;

import club.rainbowkitty.kalic0re.common.config.ModConfig;
import club.rainbowkitty.kalic0re.common.config.Setting;
import club.rainbowkitty.playerportals.portal.PortalLinks;

/**
 * This mod's server settings: each a setting of the rkcore-common settings hub, and a game rule
 * {@code playerportals:<key>} where the hub is absent. Read live, never cached.
 */
public final class PortalSettings {
    private static final ModConfig CONFIG = ModConfig.create(PlayerPortals.MOD_ID);

    /**
     * Whether a portal that only receives one-way travel, with no link of its own, is barrier
     * blocks to clients rather than light blocks, so no one walks into a portal that leads
     * nowhere.
     */
    public static final Setting<Boolean> ONE_WAY_DESTINATIONS_USE_BARRIERS =
            CONFIG.toggle("one_way_destinations_use_barriers", true).server().fallbackRule();

    private PortalSettings() {
    }

    /**
     * Registers the settings with the hub or as game rules, and reseals every portal when the
     * barrier setting changes. Call once from the initializer.
     */
    public static void register() {
        CONFIG.register();
        ONE_WAY_DESTINATIONS_USE_BARRIERS.onChange(
                (server, player, value) -> PortalLinks.resealAll(server));
    }
}
