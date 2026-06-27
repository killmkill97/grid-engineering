package dev.gridengineering;

import com.mojang.logging.LogUtils;
import dev.gridengineering.config.LaserConfig;
import dev.gridengineering.config.GridControllerConfig;
import dev.gridengineering.config.VoltageConfig;
import dev.gridengineering.laser.LaserEvents;
import dev.gridengineering.laser.LaserTransmissionAnchorChunkLoader;
import dev.gridengineering.registry.ModContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(GridEngineering.MOD_ID)
public final class GridEngineering {
    public static final String MOD_ID = "gridengineering";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GridEngineering(IEventBus modEventBus, ModContainer modContainer) {
        ModContent.register(modEventBus);
        modEventBus.addListener(LaserTransmissionAnchorChunkLoader::register);
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                VoltageConfig.SPEC,
                "gridengineering-voltage.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                LaserConfig.SPEC,
                "gridengineering-laser.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                GridControllerConfig.SPEC,
                "gridengineering-grid-controller.toml"
        );
        LaserEvents.register();
        LOGGER.info("Initializing Grid Engineering");
    }
}
