package club.rainbowkitty.playerportals.item;

import eu.pb4.polymer.core.api.other.PolymerComponent;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

import club.rainbowkitty.kalic0re.common.portal.PortalFrame;
import club.rainbowkitty.playerportals.PlayerPortals;

/** The item component a catalyst carries between its two clicks. */
public final class CatalystComponents {
    /** The frame a catalyst was last clicked on: the first of a pair, or the admin destination. */
    public static final DataComponentType<PortalFrame> ATTUNED = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, PlayerPortals.id("attuned"),
            DataComponentType.<PortalFrame>builder().persistent(PortalFrame.CODEC).build());

    static {
        // Server-side only: kept out of registry sync and out of any stack sent to a client.
        PolymerComponent.registerDataComponent(ATTUNED);
    }

    private CatalystComponents() {
    }

    /** Forces registration; the field above and the static block do the work. */
    public static void init() {
    }
}
