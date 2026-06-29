package dev.gridengineering.block;

import com.mojang.serialization.MapCodec;
import dev.gridengineering.block.entity.EntanglementLinkBlockEntity;
import dev.gridengineering.entanglement.EntanglementLinkManager;
import dev.gridengineering.entanglement.EntanglementLinkMode;
import dev.gridengineering.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Wireless, UUID-paired power bridge block.
 *
 * <p>Input/output is controlled by a block state so resource packs can swap the
 * model texture without replacing the block.</p>
 */
public final class EntanglementLinkBlock extends BaseEntityBlock {
    public static final MapCodec<EntanglementLinkBlock> CODEC =
            simpleCodec(EntanglementLinkBlock::new);
    public static final EnumProperty<EntanglementLinkMode> MODE =
            EnumProperty.create("mode", EntanglementLinkMode.class);

    public EntanglementLinkBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(MODE, EntanglementLinkMode.INPUT));
    }

    @Override
    protected MapCodec<? extends EntanglementLinkBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(MODE, EntanglementLinkMode.INPUT);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EntanglementLinkBlockEntity(pos, state);
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
        if (!level.isClientSide && blockEntityType == ModContent.ENTANGLEMENT_LINK_BLOCK_ENTITY.get()) {
            return (tickerLevel, pos, tickerState, blockEntity) ->
                    EntanglementLinkBlockEntity.serverTick(
                            (ServerLevel)tickerLevel,
                            pos,
                            tickerState,
                            (EntanglementLinkBlockEntity)blockEntity
                    );
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EntanglementLinkBlockEntity link) {
            player.openMenu(link);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) {
                EntanglementLinkManager.unregister(serverLevel, pos);
            }
            if (level.getBlockEntity(pos) instanceof EntanglementLinkBlockEntity link) {
                Containers.dropContents(level, pos, link);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
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
