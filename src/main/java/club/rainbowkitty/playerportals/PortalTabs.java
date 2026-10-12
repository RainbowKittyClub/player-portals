package club.rainbowkitty.playerportals;

import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import club.rainbowkitty.playerportals.frame.FrameBlocks;
import club.rainbowkitty.playerportals.item.CatalystItems;

/**
 * This mod's creative tab, and its items' places in vanilla's: the frame block with building
 * blocks, the catalyst with tools, the admin one with operator utilities. Polymer syncs both to
 * clients running Polymer.
 */
public final class PortalTabs {
    private PortalTabs() {
    }

    /** Registers the tab and the vanilla-tab entries. */
    public static void register() {
        CreativeModeTab tab = PolymerCreativeModeTabUtils.builder()
                .title(PlayerPortals.text().of("itemGroup." + PlayerPortals.MOD_ID + ".portals"))
                .icon(() -> new ItemStack(CatalystItems.CATALYST))
                .displayItems((params, output) -> {
                    output.accept(new ItemStack(FrameBlocks.VOID_DEEPSLATE));
                    output.accept(new ItemStack(CatalystItems.CATALYST));
                    output.accept(new ItemStack(CatalystItems.ADMIN));
                })
                .build();
        PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(PlayerPortals.id("portals"),
                tab);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
                .register(output -> output.accept(new ItemStack(FrameBlocks.VOID_DEEPSLATE)));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(output -> output.accept(new ItemStack(CatalystItems.CATALYST)));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.OP_BLOCKS).register(output -> {
            if (output.shouldShowOpRestrictedItems()) {
                output.accept(new ItemStack(CatalystItems.ADMIN));
            }
        });
    }
}
