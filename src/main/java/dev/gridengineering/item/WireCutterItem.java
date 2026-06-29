package dev.gridengineering.item;

import dev.gridengineering.block.WireBlock;
import dev.gridengineering.block.WirePortMode;
import dev.gridengineering.registry.ModTags;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class WireCutterItem extends Item {
    private static final float WIRE_DESTROY_SPEED = 1024.0F;
    private final boolean consumesDurability;

    public WireCutterItem(Properties properties) {
        this(properties, true);
    }

    public WireCutterItem(Properties properties, boolean consumesDurability) {
        super(properties);
        this.consumesDurability = consumesDurability;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        tooltipComponents.add(
                Component.translatable("tooltip.gridengineering.wire_cutter")
                        .withStyle(ChatFormatting.GRAY)
        );
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof WireBlock)) {
            return InteractionResult.PASS;
        }

        Direction direction = findTargetDirection(level, pos, context.getClickLocation(), context.getClickedFace());
        if (direction == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        if (player != null && player.isSecondaryUseActive()) {
            WirePortMode mode = WireBlock.cyclePortMode(level, pos, direction);
            player.displayClientMessage(
                    Component.translatable(
                            "message.gridengineering.port_mode",
                            Component.translatable(mode.translationKey())
                    ),
                    true
            );
            level.playSound(
                    null,
                    pos,
                    SoundEvents.COMPARATOR_CLICK,
                    SoundSource.BLOCKS,
                    0.6F,
                    switch (mode) {
                        case AUTO -> 0.8F;
                        case INPUT -> 1.0F;
                        case OUTPUT -> 1.2F;
                    }
            );
            return InteractionResult.CONSUME;
        }

        boolean cut = WireBlock.toggleConnection(level, pos, direction);
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable(cut
                            ? "message.gridengineering.connection_cut"
                            : "message.gridengineering.connection_restored"),
                    true
            );

            if (cut) {
                damageCutter(context.getItemInHand(), player, LivingEntity.getSlotForHand(context.getHand()));
            }
        }

        level.playSound(
                null,
                pos,
                SoundEvents.SHEEP_SHEAR,
                SoundSource.BLOCKS,
                0.8F,
                cut ? 0.9F : 1.15F
        );
        return InteractionResult.CONSUME;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return state.is(ModTags.WIRES) ? WIRE_DESTROY_SPEED : super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.is(ModTags.WIRES) || super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public boolean mineBlock(
            ItemStack stack,
            Level level,
            BlockState state,
            BlockPos pos,
        LivingEntity miningEntity
    ) {
        if (!level.isClientSide && state.is(ModTags.WIRES)) {
            damageCutter(stack, miningEntity, EquipmentSlot.MAINHAND);
            return true;
        }
        return super.mineBlock(stack, level, state, pos, miningEntity);
    }

    private void damageCutter(ItemStack stack, LivingEntity entity, EquipmentSlot slot) {
        if (consumesDurability) {
            stack.hurtAndBreak(1, entity, slot);
        }
    }

    private static Direction findTargetDirection(
            Level level,
            BlockPos pos,
            Vec3 clickLocation,
            Direction clickedFace
    ) {
        double localX = clickLocation.x - pos.getX() - 0.5;
        double localY = clickLocation.y - pos.getY() - 0.5;
        double localZ = clickLocation.z - pos.getZ() - 0.5;
        Direction bestDirection = null;
        double bestScore = -Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            if (!WireBlock.canTargetConnection(level, pos, direction)) {
                continue;
            }

            double score = localX * direction.getStepX()
                    + localY * direction.getStepY()
                    + localZ * direction.getStepZ();
            if (score > bestScore
                    || (score == bestScore && direction == clickedFace)) {
                bestScore = score;
                bestDirection = direction;
            }
        }

        return bestDirection;
    }
}
