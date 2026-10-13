package club.rainbowkitty.playerportals.mixin;

import eu.pb4.polymer.blocks.api.BlockResourceCreator;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the pool of client block states behind {@link PolymerBlockResourceUtils}, whose own
 * requests take the next free state of a model type, so a block can ask for a state by what it
 * is. The pool is package-private; asking it with a predicate is public API.
 */
@Mixin(value = PolymerBlockResourceUtils.class, remap = false)
public interface PolymerBlockResourceUtilsAccessor {
    /** The pool every {@code PolymerBlockResourceUtils.requestBlock} takes from. */
    @Accessor("CREATOR")
    static BlockResourceCreator playerportals$creator() {
        throw new AssertionError("PolymerBlockResourceUtilsAccessor was not applied");
    }
}
