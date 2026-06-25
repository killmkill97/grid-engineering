package dev.gridengineering.client;

import dev.gridengineering.GridEngineering;
import dev.gridengineering.block.WireBlock;
import dev.gridengineering.block.WirePortMode;
import dev.gridengineering.registry.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = GridEngineering.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (!(state.getBlock() instanceof WireBlock wireBlock)) {
                return 0xFFFFFF;
            }

            Direction direction = directionFromTintIndex(tintIndex);
            if (direction == null || level == null || pos == null) {
                return wireBlock.tintColor();
            }

            WirePortMode mode = WireBlock.getPortMode(level, pos, direction);
            return switch (mode) {
                case AUTO -> wireBlock.tintColor();
                case INPUT -> blendToward(wireBlock.tintColor(), 0xFFFFFF, 0.35F);
                case OUTPUT -> multiply(wireBlock.tintColor(), 0.45F);
            };
        }, ModContent.wireBlocks().stream()
                .map(holder -> (Block)holder.get())
                .toArray(Block[]::new));
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() instanceof WireBlock wireBlock) {
                return wireBlock.tintColor();
            }
            return 0xFFFFFF;
        }, ModContent.wireItems().stream()
                .map(holder -> (Item)holder.get())
                .toArray(Item[]::new));
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModContent.POWER_CONTROL_MENU.get(), PowerControlScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModContent.LASER_TRANSFORMER_BLOCK_ENTITY.get(),
                LaserBeamRenderer::new
        );
    }

    private static Direction directionFromTintIndex(int tintIndex) {
        return switch (tintIndex) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            case 3 -> Direction.WEST;
            case 4 -> Direction.UP;
            case 5 -> Direction.DOWN;
            default -> null;
        };
    }

    private static int blendToward(int color, int target, float amount) {
        int red = blendChannel(color >> 16 & 0xFF, target >> 16 & 0xFF, amount);
        int green = blendChannel(color >> 8 & 0xFF, target >> 8 & 0xFF, amount);
        int blue = blendChannel(color & 0xFF, target & 0xFF, amount);
        return red << 16 | green << 8 | blue;
    }

    private static int multiply(int color, float factor) {
        int red = Math.round((color >> 16 & 0xFF) * factor);
        int green = Math.round((color >> 8 & 0xFF) * factor);
        int blue = Math.round((color & 0xFF) * factor);
        return red << 16 | green << 8 | blue;
    }

    private static int blendChannel(int start, int end, float amount) {
        return Math.round(start + (end - start) * amount);
    }
}
