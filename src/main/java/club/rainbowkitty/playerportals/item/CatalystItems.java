package club.rainbowkitty.playerportals.item;

import java.util.function.BiFunction;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import club.rainbowkitty.kalic0re.common.item.ItemNames;
import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
import club.rainbowkitty.playerportals.PlayerPortals;

/** Both catalysts. */
public final class CatalystItems {
    public static final PortalCatalystItem CATALYST = register("portal_catalyst",
            PortalCatalystItem::new, new Item.Properties().rarity(Rarity.RARE));
    public static final AdminCatalystItem ADMIN = register("admin_portal_catalyst",
            AdminCatalystItem::new, new Item.Properties().rarity(Rarity.EPIC)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

    private CatalystItems() {
    }

    /** Forces registration; the fields above do the work. */
    public static void init() {
    }

    private static <T extends CatalystItem> T register(String path,
            BiFunction<ResourceKey<Item>, Item.Properties, T> factory, Item.Properties properties) {
        return ItemRegistration.register(PlayerPortals.MOD_ID, path, key -> ItemNames.named(key,
                PlayerPortals.text().of("item." + PlayerPortals.MOD_ID + "." + path),
                factory.apply(key, properties)));
    }
}
