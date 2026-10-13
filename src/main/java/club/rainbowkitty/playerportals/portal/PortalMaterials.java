package club.rainbowkitty.playerportals.portal;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import club.rainbowkitty.kalic0re.common.item.ItemNames;
import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
import club.rainbowkitty.kalic0re.common.item.PolymerModelItem;
import club.rainbowkitty.playerportals.PlayerPortals;

/**
 * The item whose model a client with the resource pack sees over every portal block: the nether
 * portal's texture tinted teal, as a slab the shape of the portal block. Never crafted, listed
 * or held; a client without the pack is shown a nether portal block instead.
 */
public final class PortalMaterials {
    public static final PolymerModelItem MATERIAL = register("portal_material");

    private PortalMaterials() {
    }

    /** Forces registration; the field above does the work. */
    public static void init() {
    }

    private static PolymerModelItem register(String path) {
        return ItemRegistration.register(PlayerPortals.MOD_ID, path, key -> ItemNames.named(key,
                PlayerPortals.text().of("item." + PlayerPortals.MOD_ID + "." + path),
                new PolymerModelItem(Items.LIGHT, PlayerPortals.id(path),
                        new Item.Properties().setId(key))));
    }
}
