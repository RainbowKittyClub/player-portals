package club.rainbowkitty.playerportals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.RecipeHolder;

import club.rainbowkitty.playerportals.item.CatalystCrafting;

/**
 * Leaves the crafting result empty for a catalyst recipe when the crafter lacks
 * {@code playerportals.craft}, the way vanilla leaves it empty for a recipe the player has not
 * unlocked under {@code doLimitedCrafting}. {@code slotChangedCraftingGrid} serves the crafting
 * table, the inventory grid and kalic0re's persistent crafting menu alike.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    private static final String SET_RECIPE_USED =
            "Lnet/minecraft/world/inventory/ResultContainer;setRecipeUsed("
            + "Lnet/minecraft/server/level/ServerPlayer;"
            + "Lnet/minecraft/world/item/crafting/RecipeHolder;)Z";

    @WrapOperation(method = "slotChangedCraftingGrid",
            at = @At(value = "INVOKE", target = SET_RECIPE_USED))
    private static boolean playerportals$gateCatalyst(ResultContainer container,
            ServerPlayer player, RecipeHolder<?> recipe, Operation<Boolean> original) {
        if (CatalystCrafting.denied(player, recipe)) {
            return false;
        }
        return original.call(container, player, recipe);
    }
}
