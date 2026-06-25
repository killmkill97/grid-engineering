package dev.gridengineering.laser;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

public final class LaserEvents {
    private LaserEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LaserEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(LaserEvents::onBlockBroken);
        NeoForge.EVENT_BUS.addListener(LaserEvents::onNeighborNotify);
        NeoForge.EVENT_BUS.addListener(LaserEvents::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(LaserEvents::onLevelUnload);
    }

    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        invalidate(event.getLevel(), event.getPos());
    }

    private static void onBlockBroken(BlockEvent.BreakEvent event) {
        invalidate(event.getLevel(), event.getPos());
    }

    private static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        invalidate(event.getLevel(), event.getPos());
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            LaserLinkManager.invalidateChunk(serverLevel, event.getChunk().getPos());
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            LaserLinkManager.clearLevel(serverLevel);
        }
    }

    private static void invalidate(
            net.minecraft.world.level.LevelAccessor level,
            net.minecraft.core.BlockPos pos
    ) {
        if (level instanceof ServerLevel serverLevel) {
            LaserLinkManager.invalidateAt(serverLevel, pos);
        }
    }
}
