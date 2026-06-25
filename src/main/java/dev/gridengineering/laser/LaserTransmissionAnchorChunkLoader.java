package dev.gridengineering.laser;

import dev.gridengineering.GridEngineering;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

public final class LaserTransmissionAnchorChunkLoader {
    private static final TicketController TICKET_CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(
                    GridEngineering.MOD_ID,
                    "laser_transmission_anchor"
            )
    );

    private LaserTransmissionAnchorChunkLoader() {
    }

    public static void register(RegisterTicketControllersEvent event) {
        event.register(TICKET_CONTROLLER);
    }

    public static void setForced(
            ServerLevel level,
            BlockPos anchorPos,
            BlockPos laserPos,
            boolean forced
    ) {
        ChunkPos chunkPos = new ChunkPos(laserPos);
        TICKET_CONTROLLER.forceChunk(
                level,
                anchorPos,
                chunkPos.x,
                chunkPos.z,
                forced,
                true
        );
    }
}
