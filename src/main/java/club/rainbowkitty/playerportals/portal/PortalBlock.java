package club.rainbowkitty.playerportals.portal;

import java.util.Map;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import club.rainbowkitty.playerportals.display.FlatPortalHolder;
import club.rainbowkitty.playerportals.frame.FrameFinder;
import club.rainbowkitty.playerportals.frame.PortalPlane;

/**
 * The block a lit portal's interior is made of. Vanilla's own portal processing carries an entity
 * through: the block only marks the entity as inside, and answers for its destination once the
 * entity has stood in it for {@link #getPortalTransitionTime} ticks, which is none.
 *
 * <p>A client sees {@code nether_portal} in the same axis for an upright portal, which renders,
 * culls and sounds as vanilla's. A flat portal has no vanilla state to borrow, so the client sees
 * a level-11 light block (invisible, no collision, the same light) and a block display per block
 * ({@link FlatPortalHolder}) draws the portal lying down.
 */
public final class PortalBlock extends Block implements Portal, PolymerBlock,
        BlockWithElementHolder {
    public static final MapCodec<PortalBlock> CODEC = simpleCodec(PortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    private static final int LIGHT = 11;
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Map.of(
            Direction.Axis.X, Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0),
            Direction.Axis.Z, Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0),
            Direction.Axis.Y, Block.box(0.0, 6.0, 0.0, 16.0, 10.0, 16.0));
    private static final Map<Direction.Axis, BlockState> CLIENT_STATES = Map.of(
            Direction.Axis.X, Blocks.NETHER_PORTAL.defaultBlockState()
                    .setValue(NetherPortalBlock.AXIS, Direction.Axis.X),
            Direction.Axis.Z, Blocks.NETHER_PORTAL.defaultBlockState()
                    .setValue(NetherPortalBlock.AXIS, Direction.Axis.Z),
            Direction.Axis.Y, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT));

    public PortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    /**
     * The nether portal's properties under {@code key}: no collision, unbreakable in survival,
     * light 11, a wall to pistons, nothing to drop. No random ticks: nothing spawns here.
     */
    public static Properties properties(ResourceKey<Block> key) {
        return Properties.of().noCollision().strength(-1.0F).sound(SoundType.GLASS)
                .lightLevel(state -> LIGHT).pushReaction(PushReaction.BLOCK).noLootTable()
                .setId(key);
    }

    /** Whether {@code state} is this block lying in {@code plane}. */
    public static boolean isPortal(BlockState state, PortalPlane plane) {
        return state.is(PortalBlocks.PORTAL) && state.getValue(AXIS) == plane.axis();
    }

    /** This block's state for a portal in {@code plane}. */
    public static BlockState stateFor(PortalPlane plane) {
        return PortalBlocks.PORTAL.defaultBlockState().setValue(AXIS, plane.axis());
    }

    @Override
    protected MapCodec<PortalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPES.get(state.getValue(AXIS));
    }

    // Air once an in-plane neighbour is neither portal of this plane nor the frame block the
    // portal was lit with; the rest of the interior then cascades after it, as a nether portal's
    // does. A frame block that turned into another block (copper weathering, powder setting)
    // keeps its shape but not the pattern, so the portal fades now, not under its next user.
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
            ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
            BlockState neighbourState, RandomSource random) {
        PortalPlane plane = PortalPlane.of(state.getValue(AXIS));
        if (!plane.spans(direction.getAxis()) || isPortal(neighbourState, plane)) {
            return state;
        }
        if (!FrameFinder.isFrameBlock(neighbourState, level, neighbourPos)
                || level instanceof ServerLevel serverLevel && !PortalRegistry.of(
                        serverLevel.getServer()).ringMatches(serverLevel.dimension(), pos,
                        neighbourPos, neighbourState)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
            boolean movedByPiston) {
        PortalRegistry.of(level.getServer()).onPortalBlockRemoved(level, pos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    @Override
    public @Nullable TeleportTransition getPortalDestination(ServerLevel level, Entity entity,
            BlockPos entryPos) {
        return PortalTravel.destination(level, entity, entryPos);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
            boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return CLIENT_STATES.get(state.getValue(AXIS));
    }

    @Override
    public @Nullable ElementHolder createElementHolder(ServerLevel level, BlockPos pos,
            BlockState state) {
        return state.getValue(AXIS) == Direction.Axis.Y ? new FlatPortalHolder() : null;
    }
}
