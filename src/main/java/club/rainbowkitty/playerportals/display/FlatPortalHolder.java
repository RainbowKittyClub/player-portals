package club.rainbowkitty.playerportals.display;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One flat portal block as a client sees it: a block display of a nether portal block turned
 * to lie down, its two faces at the block's mid-height. Static, so Polymer never ticks it; the
 * display samples the light of the level-11 stand-in it sits in.
 */
public final class FlatPortalHolder extends ElementHolder {
    private static final BlockState LOOK = Blocks.NETHER_PORTAL.defaultBlockState()
            .setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
    private static final Quaternionf LAID_FLAT =
            new Quaternionf().rotationX((float) Math.toRadians(90.0));
    // A block display draws its model from the entity's position as the model's origin corner,
    // and the holder sits at the block's centre: turn the model about its own centre, then move
    // that centre onto the holder's.
    private static final Vector3f CENTRED =
            LAID_FLAT.transform(new Vector3f(0.5F, 0.5F, 0.5F)).negate();

    public FlatPortalHolder() {
        BlockDisplayElement display = new BlockDisplayElement(LOOK);
        display.setLeftRotation(LAID_FLAT);
        display.setTranslation(CENTRED);
        addElement(display);
    }
}
