package dev.gridengineering.item;

import dev.gridengineering.block.WireBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

public final class WireBlockItem extends BlockItem {
    public WireBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return ((WireBlock)this.getBlock()).displayName();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player != null
                && player.isSecondaryUseActive()
                && context.getLevel().getBlockState(pos).getBlock() instanceof WireBlock) {
            Direction direction = context.getClickedFace();
            if (context.getLevel().isClientSide) {
                return InteractionResult.SUCCESS;
            }

            boolean extended = WireBlock.toggleManualExtension(context.getLevel(), pos, direction);
            player.displayClientMessage(
                    Component.translatable(extended
                            ? "message.gridengineering.wire_extended"
                            : "message.gridengineering.wire_retracted"),
                    true
            );
            return InteractionResult.CONSUME;
        }

        return super.useOn(context);
    }
}
