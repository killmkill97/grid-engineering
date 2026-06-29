package dev.gridengineering.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.gridengineering.item.EntangledElectronItem;
import dev.gridengineering.registry.ModContent;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers Grid Engineering server commands.
 */
public final class GridEngineeringCommands {
    private GridEngineeringCommands() {
    }

    /**
     * Hooks command registration into NeoForge's game event bus.
     */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(GridEngineeringCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /**
     * Registers /get_random_entangled_electron.
     */
    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("get_random_entangled_electron")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> giveRandomEntangledElectron(context.getSource()))
        );
    }

    /**
     * Gives the command executor one Entangled Electron pair with a fresh UUID.
     */
    private static int giveRandomEntangledElectron(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UUID uuid = UUID.randomUUID();
        ItemStack stack = new ItemStack(ModContent.ENTANGLED_ELECTRON.get(), 2);
        EntangledElectronItem.setEntanglementId(stack, uuid);

        if (!player.addItem(stack)) {
            ItemEntity dropped = player.drop(stack, false);
            if (dropped != null) {
                dropped.setNoPickUpDelay();
                dropped.setTarget(player.getUUID());
            }
        }

        source.sendSuccess(
                () -> Component.translatable(
                        "commands.gridengineering.get_random_entangled_electron.success",
                        uuid.toString()
                ),
                false
        );
        return 1;
    }
}
