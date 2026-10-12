package club.rainbowkitty.playerportals.mixin;

import java.util.Optional;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;

import club.rainbowkitty.playerportals.item.CatalystCrafting;

/**
 * Keeps a crafter from making a catalyst: it has no player to hold {@code playerportals.craft},
 * so it never qualifies. Covers the crafter's own cycle and its menu's preview.
 */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {
    @ModifyReturnValue(method = "getPotentialResults", at = @At("RETURN"))
    private static Optional<RecipeHolder<CraftingRecipe>> playerportals$noCatalyst(
            Optional<RecipeHolder<CraftingRecipe>> result) {
        return result.filter(recipe -> !CatalystCrafting.isCatalystRecipe(recipe));
    }
}
