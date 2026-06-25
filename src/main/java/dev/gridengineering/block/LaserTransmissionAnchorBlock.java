package dev.gridengineering.block;

import com.mojang.serialization.MapCodec;
import dev.gridengineering.laser.LaserTransmissionAnchorChunkLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LaserTransmissionAnchorBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape SHAPE_DOWN = Shapes.or(
            box(4.0D, 0.0D, 4.0D, 12.0D, 1.0D, 12.0D),
            box(9.0D, 1.0D, 5.0D, 11.0D, 2.0D, 11.0D),
            box(5.0D, 1.0D, 5.0D, 8.0D, 2.0D, 7.0D),
            box(5.0D, 1.0D, 8.0D, 8.0D, 2.0D, 11.0D)
    );
    private static final VoxelShape SHAPE_UP = Shapes.or(
            box(4.0D, 15.0D, 4.0D, 12.0D, 16.0D, 12.0D),
            box(9.0D, 14.0D, 5.0D, 11.0D, 15.0D, 11.0D),
            box(5.0D, 14.0D, 5.0D, 8.0D, 15.0D, 7.0D),
            box(5.0D, 14.0D, 8.0D, 8.0D, 15.0D, 11.0D)
    );
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            box(4.0D, 4.0D, 0.0D, 12.0D, 12.0D, 1.0D),
            box(9.0D, 5.0D, 1.0D, 11.0D, 11.0D, 2.0D),
            box(5.0D, 9.0D, 1.0D, 8.0D, 11.0D, 2.0D),
            box(5.0D, 5.0D, 1.0D, 8.0D, 8.0D, 2.0D)
    );
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            box(4.0D, 4.0D, 15.0D, 12.0D, 12.0D, 16.0D),
            box(9.0D, 5.0D, 14.0D, 11.0D, 11.0D, 15.0D),
            box(5.0D, 5.0D, 14.0D, 8.0D, 7.0D, 15.0D),
            box(5.0D, 8.0D, 14.0D, 8.0D, 11.0D, 15.0D)
    );
    private static final VoxelShape SHAPE_WEST = Shapes.or(
            box(0.0D, 4.0D, 4.0D, 1.0D, 12.0D, 12.0D),
            box(1.0D, 5.0D, 9.0D, 2.0D, 11.0D, 11.0D),
            box(1.0D, 9.0D, 5.0D, 2.0D, 11.0D, 8.0D),
            box(1.0D, 5.0D, 5.0D, 2.0D, 8.0D, 8.0D)
    );
    private static final VoxelShape SHAPE_EAST = Shapes.or(
            box(15.0D, 4.0D, 4.0D, 16.0D, 12.0D, 12.0D),
            box(14.0D, 5.0D, 9.0D, 15.0D, 11.0D, 11.0D),
            box(14.0D, 5.0D, 5.0D, 15.0D, 7.0D, 8.0D),
            box(14.0D, 8.0D, 5.0D, 15.0D, 11.0D, 8.0D)
    );
    public static final MapCodec<LaserTransmissionAnchorBlock> CODEC =
            simpleCodec(LaserTransmissionAnchorBlock::new);

    public LaserTransmissionAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(FACING, Direction.DOWN)
                        .setValue(ACTIVE, false)
        );
    }

    @Override
    protected MapCodec<? extends LaserTransmissionAnchorBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return switch (state.getValue(FACING)) {
            case DOWN -> SHAPE_DOWN;
            case UP -> SHAPE_UP;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return this.getShape(state, level, pos, context);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Direction facing = findLaserDirection(
                context.getLevel(),
                pos,
                context.getClickedFace().getOpposite()
        );
        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(ACTIVE, hasLaser(context.getLevel(), pos, facing));
    }

    @Override
    protected void onPlace(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock()) && level instanceof ServerLevel serverLevel) {
            LaserTransmissionAnchorChunkLoader.setForced(
                    serverLevel,
                    pos,
                    laserPos(pos, state),
                    state.getValue(ACTIVE)
            );
        }
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide || !neighborPos.equals(laserPos(pos, state))) {
            return;
        }

        boolean active = hasLaser(level, pos, state.getValue(FACING));
        if (state.getValue(ACTIVE) == active) {
            return;
        }

        level.setBlock(pos, state.setValue(ACTIVE, active), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel serverLevel) {
            LaserTransmissionAnchorChunkLoader.setForced(
                    serverLevel,
                    pos,
                    laserPos(pos, state),
                    active
            );
        }
    }

    @Override
    protected void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            LaserTransmissionAnchorChunkLoader.setForced(
                    serverLevel,
                    pos,
                    laserPos(pos, state),
                    false
            );
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    private static Direction findLaserDirection(
            Level level,
            BlockPos anchorPos,
            Direction preferredDirection
    ) {
        if (hasLaser(level, anchorPos, preferredDirection)) {
            return preferredDirection;
        }
        for (Direction direction : Direction.values()) {
            if (hasLaser(level, anchorPos, direction)) {
                return direction;
            }
        }
        return preferredDirection;
    }

    private static boolean hasLaser(
            Level level,
            BlockPos anchorPos,
            Direction direction
    ) {
        return level.getBlockState(anchorPos.relative(direction)).getBlock()
                instanceof LaserTransformerBlock;
    }

    private static BlockPos laserPos(BlockPos anchorPos, BlockState state) {
        return anchorPos.relative(state.getValue(FACING));
    }

    public static boolean isLaserAnchored(Level level, BlockPos laserPos) {
        for (Direction direction : Direction.values()) {
            BlockPos anchorPos = laserPos.relative(direction);
            BlockState anchorState = level.getBlockState(anchorPos);
            if (anchorState.getBlock() instanceof LaserTransmissionAnchorBlock
                    && anchorState.getValue(ACTIVE)
                    && anchorPos.relative(anchorState.getValue(FACING)).equals(laserPos)) {
                return true;
            }
        }
        return false;
    }
}
