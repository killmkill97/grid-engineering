package dev.gridengineering.block;

import com.mojang.serialization.MapCodec;
import dev.gridengineering.block.entity.WireBlockEntity;
import dev.gridengineering.energy.ModDamageTypes;
import dev.gridengineering.energy.PowerEndpointAccess;
import dev.gridengineering.energy.WireEnergyTransfer;
import java.math.BigDecimal;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

import dev.gridengineering.registry.ModContent;

public class WireBlock extends PipeBlock implements EntityBlock {
    private final MapCodec<WireBlock> codec;
    private final WireMaterial material;
    private final WireGauge gauge;
    private final WireCoating coating;
    private final String voltageTierName;
    private final long ratedVoltage;
    private final long maxAmps;
    private final long lossPerMeterPerAmpPpm;

    public WireBlock(BlockBehaviour.Properties properties) {
        this(properties, WireMaterial.TIN, WireGauge.MM_1, WireCoating.BARE);
    }

    public WireBlock(
            BlockBehaviour.Properties properties,
            WireMaterial material,
            WireGauge gauge,
            WireCoating coating
    ) {
        super(gauge.apothem(coating), properties);
        this.material = material;
        this.gauge = gauge;
        this.coating = coating;
        this.voltageTierName = material.voltageTierName();
        this.ratedVoltage = material.ratedVoltage();
        this.maxAmps = Math.multiplyExact(material.baseAmps(), gauge.millimeters());
        this.lossPerMeterPerAmpPpm =
                coating.applyLossMultiplier(material.lossPerMeterPerAmpPpm());
        this.codec = simpleCodec(newProperties ->
                new WireBlock(newProperties, material, gauge, coating));
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(NORTH, false)
                        .setValue(EAST, false)
                        .setValue(SOUTH, false)
                        .setValue(WEST, false)
                        .setValue(UP, false)
                        .setValue(DOWN, false)
        );
    }

    @Override
    protected MapCodec<? extends WireBlock> codec() {
        return this.codec;
    }

    public String voltageTierName() {
        return this.voltageTierName;
    }

    public WireMaterial material() {
        return this.material;
    }

    public WireGauge gauge() {
        return this.gauge;
    }

    public WireCoating coating() {
        return this.coating;
    }

    public int tintColor() {
        return this.material.tintColor();
    }

    @Override
    public MutableComponent getName() {
        return this.displayName();
    }

    public MutableComponent displayName() {
        return Component.translatable(
                this.coating.isCoated()
                        ? "block.gridengineering.coated_wire"
                        : "block.gridengineering.wire",
                Component.translatable("material.gridengineering." + this.material.id()),
                this.gauge.millimeters()
        );
    }

    public long ratedVoltage() {
        return this.ratedVoltage;
    }

    public long maxAmps() {
        return this.maxAmps;
    }

    public long lossPerMeterPerAmpPpm() {
        return this.lossPerMeterPerAmpPpm;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (touchesWireShape(state, level, pos, entity)) {
            this.shockPlayer(level, pos, entity);
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        this.shockPlayer(level, pos, entity);
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = this.defaultBlockState();

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            state = state.setValue(
                    PROPERTY_BY_DIRECTION.get(direction),
                    canConnect(level, pos, direction, neighborPos, neighborState)
            );
        }

        return state;
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        return state.setValue(
                PROPERTY_BY_DIRECTION.get(direction),
                canConnect(level, pos, direction, neighborPos, neighborState)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WireBlockEntity(pos, state);
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
                        "tooltip.gridengineering.wire.rating",
                        this.gauge.millimeters(),
                        this.voltageTierName,
                        this.maxAmps
                ).withStyle(ChatFormatting.GRAY)
        );
        tooltipComponents.add(
                Component.translatable("tooltip.gridengineering.copper_wire.extend")
                        .withStyle(ChatFormatting.GRAY)
        );
        tooltipComponents.add(
                Component.translatable(
                        "tooltip.gridengineering.wire.loss",
                        BigDecimal.valueOf(this.lossPerMeterPerAmpPpm, 4)
                                .stripTrailingZeros()
                                .toPlainString()
                ).withStyle(ChatFormatting.DARK_GRAY)
        );
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType
    ) {
        if (!level.isClientSide && blockEntityType == ModContent.WIRE_BLOCK_ENTITY.get()) {
            return (tickerLevel, pos, tickerState, blockEntity) ->
                    WireBlockEntity.serverTick(
                            (net.minecraft.server.level.ServerLevel)tickerLevel,
                            pos,
                            tickerState,
                            (WireBlockEntity)blockEntity
                    );
        }
        return null;
    }

    public static boolean toggleConnection(Level level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        if (!(level.getBlockState(pos).getBlock() instanceof WireBlock)) {
            return false;
        }

        WireBlockEntity wire = getWireBlockEntity(level, pos);
        if (wire == null || !canPotentiallyConnect(level, pos, direction)) {
            return false;
        }

        boolean cut = !wire.isSideCut(direction);
        wire.setSideCut(direction, cut);
        WireBlockEntity neighbor = getWireBlockEntity(level, neighborPos);
        if (neighbor != null) {
            neighbor.setSideCut(direction.getOpposite(), cut);
        }

        refreshConnections(level, pos);
        if (neighbor != null) {
            refreshConnections(level, neighborPos);
            neighbor.sync();
        }
        wire.sync();
        return cut;
    }

    public static boolean toggleManualExtension(Level level, BlockPos pos, Direction direction) {
        WireBlockEntity wire = getWireBlockEntity(level, pos);
        if (wire == null || level.getBlockState(pos.relative(direction)).getBlock() instanceof WireBlock) {
            return false;
        }

        boolean extended = !wire.isSideManuallyExtended(direction);
        wire.setSideManuallyExtended(direction, extended);
        wire.setSideCut(direction, false);
        refreshConnections(level, pos);
        wire.sync();
        return extended;
    }

    public static WirePortMode cyclePortMode(Level level, BlockPos pos, Direction direction) {
        WireBlockEntity wire = getWireBlockEntity(level, pos);
        return wire == null ? WirePortMode.AUTO : wire.cyclePortMode(direction);
    }

    public static WirePortMode getPortMode(BlockGetter level, BlockPos pos, Direction direction) {
        WireBlockEntity wire = getWireBlockEntity(level, pos);
        return wire == null ? WirePortMode.AUTO : wire.getPortMode(direction);
    }

    public static boolean isConnected(BlockState state, Direction direction) {
        return state.getBlock() instanceof WireBlock
                && state.getValue(PROPERTY_BY_DIRECTION.get(direction));
    }

    public static boolean canTargetConnection(Level level, BlockPos pos, Direction direction) {
        if (isConnected(level.getBlockState(pos), direction)) {
            return true;
        }

        BlockPos neighborPos = pos.relative(direction);
        WireBlockEntity wire = getWireBlockEntity(level, pos);
        return level.getBlockState(neighborPos).getBlock() instanceof WireBlock
                || hasEnergyEndpoint(level, neighborPos, direction.getOpposite())
                || wire != null && wire.isSideManuallyExtended(direction);
    }

    private static boolean canConnect(
            BlockGetter level,
            BlockPos pos,
            Direction direction,
            BlockPos neighborPos,
            BlockState neighborState
    ) {
        if (!(neighborState.getBlock() instanceof WireBlock)) {
            WireBlockEntity wire = getWireBlockEntity(level, pos);
            return wire != null
                    && !wire.isSideCut(direction)
                    && (wire.isSideManuallyExtended(direction)
                    || hasEnergyEndpoint(level, neighborPos, direction.getOpposite()));
        }

        WireBlockEntity wire = getWireBlockEntity(level, pos);
        WireBlockEntity neighbor = getWireBlockEntity(level, neighborPos);
        return (wire == null || !wire.isSideCut(direction))
                && (neighbor == null || !neighbor.isSideCut(direction.getOpposite()));
    }

    public static void refreshConnections(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WireBlock)) {
            return;
        }

        BlockState updatedState = state;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            updatedState = updatedState.setValue(
                    PROPERTY_BY_DIRECTION.get(direction),
                    canConnect(level, pos, direction, neighborPos, level.getBlockState(neighborPos))
            );
        }

        if (updatedState != state) {
            level.setBlock(pos, updatedState, Block.UPDATE_CLIENTS);
        }
    }

    private static WireBlockEntity getWireBlockEntity(BlockGetter level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof WireBlockEntity wire ? wire : null;
    }

    private static boolean canPotentiallyConnect(Level level, BlockPos pos, Direction direction) {
        return canTargetConnection(level, pos, direction);
    }

    private static boolean hasEnergyEndpoint(BlockGetter level, BlockPos pos, Direction side) {
        return level instanceof Level actualLevel
                && PowerEndpointAccess.exists(actualLevel, pos, side);
    }

    private static boolean touchesWireShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            Entity entity
    ) {
        return state.getShape(level, pos).toAabbs().stream()
                .map(bounds -> bounds.move(pos))
                .anyMatch(bounds -> bounds.intersects(entity.getBoundingBox()));
    }

    private void shockPlayer(Level level, BlockPos pos, Entity entity) {
        if (this.coating != WireCoating.BARE
                || !(level instanceof ServerLevel serverLevel)
                || !(entity instanceof Player player)
                || player.isCreative()
                || player.isSpectator()
                || !WireEnergyTransfer.isPowerFlowing(serverLevel, pos)) {
            return;
        }

        player.hurt(level.damageSources().source(ModDamageTypes.ELECTROCUTION), 4.0F);
    }
}
