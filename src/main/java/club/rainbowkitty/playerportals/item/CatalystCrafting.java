package club.rainbowkitty.playerportals.item;

import java.util.List;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import club.rainbowkitty.playerportals.PlayerPortals;
import club.rainbowkitty.playerportals.PortalPermissions;

/**
 * The crafting gate: whether a recipe is this mod's and whether a player may take its result,
 * asked by the mixins on the crafting result and the crafter; and the recipe book, which gets
 * the recipe on join by the same permission, so a later grant or denial shows at the next join.
 */
public final class CatalystCrafting {
    private static final ResourceKey<Recipe<?>> RECIPE =
            ResourceKey.create(Registries.RECIPE, PlayerPortals.id("portal_catalyst"));

    private CatalystCrafting() {
    }

    /**
     * Awards the recipe to, or takes it from, each player's recipe book as they join. Runs after
     * every default-phase join listener: a permission mod attaches the player's data in its own,
     * registered after this mod's, and before that the node reads as undefined.
     */
    public static void register() {
        Identifier phase = PlayerPortals.id("after_permissions");
        ServerPlayConnectionEvents.JOIN.addPhaseOrdering(Event.DEFAULT_PHASE, phase);
        ServerPlayConnectionEvents.JOIN.register(phase, (handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (PortalPermissions.mayCraft(player)) {
                player.awardRecipesByKey(List.of(RECIPE));
            } else {
                server.getRecipeManager().byKey(RECIPE)
                        .ifPresent(recipe -> player.resetRecipes(List.of(recipe)));
            }
        });
    }

    /** Whether {@code recipe} is one of this mod's, by its id's namespace. */
    public static boolean isCatalystRecipe(RecipeHolder<?> recipe) {
        return PlayerPortals.MOD_ID.equals(recipe.id().identifier().getNamespace());
    }

    /** Whether {@code player} may not take the result of {@code recipe}. */
    public static boolean denied(ServerPlayer player, RecipeHolder<?> recipe) {
        return isCatalystRecipe(recipe) && !PortalPermissions.mayCraft(player);
    }
}
