package dev.gridengineering.block;

import com.mojang.serialization.MapCodec;
import dev.gridengineering.block.entity.GridControllerBlockEntity;
import dev.gridengineering.gridcontroller.GridControllerTier;
import dev.gridengineering.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

public final class GridControllerBlock extends BaseEntityBlock {
    public static final MapCodec<GridControllerBlock> CODEC = simpleCodec(GridControllerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final GridControllerTier tier;

    public GridControllerBlock(BlockBehaviour.Properties properties) {
        this(properties, GridControllerTier.MK1);
    }

    public GridControllerBlock(BlockBehaviour.Properties properties, GridControllerTier tier) {
        super(properties);
        this.tier = tier;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends GridControllerBlock> codec() {
        return CODEC;
    }

    public GridControllerTier tier() {
        return this.tier;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(
                FACING,
                context.getNearestLookingDirection().getOpposite()
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GridControllerBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType
    ) {
        if (!level.isClientSide && blockEntityType == ModContent.GRID_CONTROLLER_BLOCK_ENTITY.get()) {
            return (tickerLevel, pos, tickerState, blockEntity) ->
                    GridControllerBlockEntity.serverTick(
                            pos,
                            tickerState,
                            (GridControllerBlockEntity)blockEntity
                    );
        }
        return null;
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        level.invalidateCapabilities(pos);
    }
}
