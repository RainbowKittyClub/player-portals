package club.rainbowkitty.playerportals.portal;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import club.rainbowkitty.kalic0re.common.item.ItemNames;
import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
import club.rainbowkitty.kalic0re.common.item.PolymerModelItem;
import club.rainbowkitty.playerportals.PlayerPortals;

/**
 * Items that exist to be shown by displays, never crafted, listed or held: the portal material,
 * the nether portal's texture tinted teal as a slab the shape of the portal block, which a
 * client with the resource pack sees over every portal block (one without is shown a nether
 * portal block instead); and the puddle a frame block's drip leaves, a flat teal bar.
 */
public final class PortalMaterials {
    public static final PolymerModelItem MATERIAL = register("portal_material");
    public static final PolymerModelItem PUDDLE = register("puddle");

    private PortalMaterials() {
    }

    /** Forces registration; the fields above do the work. */
    public static void init() {
    }

    private static PolymerModelItem register(String path) {
        return ItemRegistration.register(PlayerPortals.MOD_ID, path, key -> ItemNames.named(key,
                PlayerPortals.text().of("item." + PlayerPortals.MOD_ID + "." + path),
                new PolymerModelItem(Items.LIGHT, PlayerPortals.id(path),
                        new Item.Properties().setId(key))));
    }
}
