package dev.gridengineering.block;

import com.mojang.serialization.MapCodec;
import dev.gridengineering.block.entity.LaserTransformerBlockEntity;
import dev.gridengineering.laser.LaserLinkManager;
import dev.gridengineering.laser.LaserRole;
import dev.gridengineering.laser.LaserTier;
import dev.gridengineering.registry.ModContent;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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

public final class LaserTransformerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final LaserTier tier;
    private final LaserRole role;
    private final MapCodec<LaserTransformerBlock> codec;

    public LaserTransformerBlock(
            BlockBehaviour.Properties properties,
            LaserTier tier,
            LaserRole role
    ) {
        super(properties);
        this.tier = tier;
        this.role = role;
        this.codec = simpleCodec(newProperties ->
                new LaserTransformerBlock(newProperties, tier, role));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public LaserTier tier() {
        return this.tier;
    }

    public LaserRole role() {
        return this.role;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        tooltipComponents.add(
                Component.translatable(
                                "tooltip.gridengineering.laser.max_voltage",
                                formatVoltage(this.tier.maxVoltage())
                        )
                        .withStyle(ChatFormatting.GRAY)
        );
        tooltipComponents.add(
                Component.translatable(
                                "tooltip.gridengineering.laser.max_distance",
                                NumberFormat.getIntegerInstance(Locale.ROOT)
                                        .format(this.tier.maxDistance())
                        )
                        .withStyle(ChatFormatting.GRAY)
        );
    }

    @Override
    protected MapCodec<? extends LaserTransformerBlock> codec() {
        return this.codec;
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
        return new LaserTransformerBlockEntity(pos, state);
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
        if (!level.isClientSide && blockEntityType == ModContent.LASER_TRANSFORMER_BLOCK_ENTITY.get()) {
            return (tickerLevel, pos, tickerState, blockEntity) ->
                    LaserTransformerBlockEntity.serverTick(
                            (ServerLevel)tickerLevel,
                            pos,
                            tickerState,
                            (LaserTransformerBlockEntity)blockEntity
                    );
        }
        return null;
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
            LaserLinkManager.unregister(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static Component formatVoltage(long voltage) {
        if (voltage == Long.MAX_VALUE) {
            return Component.translatable("tooltip.gridengineering.unlimited");
        }
        return Component.literal(NumberFormat.getIntegerInstance(Locale.ROOT).format(voltage) + " V");
    }
}
