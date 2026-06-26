package dev.gridengineering.item;

import dev.gridengineering.block.WireBlock;
import dev.gridengineering.energy.WireEnergyTransfer;
import dev.gridengineering.energy.WireEnergyTransfer.NetworkSnapshot;
import dev.gridengineering.energy.WireEnergyTransfer.TransferRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class NetworkMonitorItem extends Item {
    public NetworkMonitorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        tooltipComponents.add(
                Component.translatable("tooltip.gridengineering.network_monitor")
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
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.CONSUME;
        }

        NetworkSnapshot snapshot = WireEnergyTransfer.inspectNetwork((ServerLevel)level, pos);
        showSnapshot(player, snapshot);
        return InteractionResult.CONSUME;
    }

    private static void showSnapshot(Player player, NetworkSnapshot snapshot) {
        player.sendSystemMessage(
                Component.translatable("message.gridengineering.network_monitor.header")
                        .withStyle(ChatFormatting.GOLD)
        );
        player.sendSystemMessage(
                Component.translatable(
                        "message.gridengineering.network_monitor.voltage",
                        snapshot.activeVoltage() > 0L
                                ? formatNumber(snapshot.activeVoltage())
                                : "-",
                        snapshot.voltageTierName(),
                        formatNumber(snapshot.maxVoltage())
                ).withStyle(ChatFormatting.YELLOW)
        );
        player.sendSystemMessage(
                Component.translatable(
                        "message.gridengineering.network_monitor.amperage",
                        formatMicroAmperage(snapshot.inspectedWireMicroAmps()),
                        formatNumber(snapshot.inspectedWireMaxAmps())
                ).withStyle(ChatFormatting.AQUA)
        );
        player.sendSystemMessage(
                Component.translatable(
                        "message.gridengineering.network_monitor.wire_load",
                        formatMicroAmperage(snapshot.inspectedWireMicroAmps())
                ).withStyle(ChatFormatting.BLUE)
        );
        player.sendSystemMessage(
                Component.translatable(
                        "message.gridengineering.network_monitor.grid_controller",
                        Component.translatable(snapshot.gridControllers().isEmpty()
                                ? "message.gridengineering.network_monitor.grid_controller.disconnected"
                                : "message.gridengineering.network_monitor.grid_controller.connected")
                ).withStyle(snapshot.gridControllers().isEmpty()
                        ? ChatFormatting.DARK_GRAY
                        : ChatFormatting.LIGHT_PURPLE)
        );
        for (BlockPos controllerPos : snapshot.gridControllers()) {
            player.sendSystemMessage(
                    Component.translatable(
                            "message.gridengineering.network_monitor.grid_controller_position",
                            formatPosition(controllerPos)
                    ).withStyle(ChatFormatting.LIGHT_PURPLE)
            );
        }
        player.sendSystemMessage(
                Component.translatable(
                        "message.gridengineering.network_monitor.input",
                        formatNumber(snapshot.inputPower())
                ).withStyle(ChatFormatting.GREEN)
        );
        player.sendSystemMessage(
                Component.translatable(
                        "message.gridengineering.network_monitor.output",
                        formatNumber(snapshot.outputPower())
                ).withStyle(ChatFormatting.GREEN)
        );
        player.sendSystemMessage(
                Component.translatable(
                        "message.gridengineering.network_monitor.loss",
                        formatNumber(snapshot.lossPower()),
                        formatPercent(snapshot.lossPower(), snapshot.inputPower())
                ).withStyle(ChatFormatting.RED)
        );
        player.sendSystemMessage(
                Component.translatable(
                        "message.gridengineering.network_monitor.wires",
                        snapshot.wireCount()
                ).withStyle(ChatFormatting.GRAY)
        );

        if (snapshot.transfers().isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable("message.gridengineering.network_monitor.no_transfer")
                            .withStyle(ChatFormatting.DARK_GRAY)
            );
            return;
        }

        player.sendSystemMessage(
                Component.translatable("message.gridengineering.network_monitor.routes")
                        .withStyle(ChatFormatting.WHITE)
        );
        for (TransferRecord transfer : snapshot.transfers()) {
            player.sendSystemMessage(
                    Component.translatable(
                            "message.gridengineering.network_monitor.route",
                            formatNumber(transfer.inputPower()),
                            formatNumber(transfer.outputPower()),
                            formatNumber(transfer.lossPower()),
                            transfer.wireDistance(),
                            formatNumber(transfer.voltage()),
                            formatPosition(transfer.source()),
                            formatPosition(transfer.destination())
                    ).withStyle(ChatFormatting.GRAY)
            );
        }
    }

    private static String formatMicroAmperage(long microAmps) {
        return BigDecimal.valueOf(microAmps)
                .divide(
                        BigDecimal.valueOf(WireEnergyTransfer.MICRO_AMPS_PER_AMP),
                        3,
                        RoundingMode.HALF_UP
                )
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatPercent(long part, long total) {
        if (part <= 0L || total <= 0L) {
            return "0";
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100L))
                .divide(BigDecimal.valueOf(total), 3, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String formatPosition(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}
