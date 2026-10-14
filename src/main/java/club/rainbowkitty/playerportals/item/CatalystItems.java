package club.rainbowkitty.playerportals.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import club.rainbowkitty.playerportals.PlayerPortals;

/** Both catalysts. */
public final class CatalystItems {
    public static final PortalCatalystItem CATALYST = PlayerPortals.registerItem(
            "portal_catalyst",
            key -> new PortalCatalystItem(key, new Item.Properties().rarity(Rarity.RARE)));
    public static final AdminCatalystItem ADMIN = PlayerPortals.registerItem(
            "admin_portal_catalyst",
            key -> new AdminCatalystItem(key, new Item.Properties().rarity(Rarity.EPIC)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    private CatalystItems() {
    }

    /** Forces registration; the fields above do the work. */
    public static void init() {
    }
}
