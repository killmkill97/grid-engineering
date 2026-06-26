package dev.gridengineering.integration.jade;

import dev.gridengineering.block.CurrentRegulatorBlock;
import dev.gridengineering.block.LaserTransformerBlock;
import dev.gridengineering.block.LaserTransmissionAnchorBlock;
import dev.gridengineering.block.GridControllerBlock;
import dev.gridengineering.block.TestBatteryBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public final class GridEngineeringJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(LongPowerComponentProvider.INSTANCE, TestBatteryBlock.class);
        registration.registerBlockDataProvider(LongPowerComponentProvider.INSTANCE, CurrentRegulatorBlock.class);
        registration.registerBlockDataProvider(LongPowerComponentProvider.INSTANCE, GridControllerBlock.class);
        registration.registerBlockDataProvider(LongPowerComponentProvider.INSTANCE, LaserTransformerBlock.class);
        registration.registerBlockDataProvider(LongPowerComponentProvider.INSTANCE, LaserTransmissionAnchorBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(LongPowerComponentProvider.INSTANCE, TestBatteryBlock.class);
        registration.registerBlockComponent(LongPowerComponentProvider.INSTANCE, CurrentRegulatorBlock.class);
        registration.registerBlockComponent(LongPowerComponentProvider.INSTANCE, GridControllerBlock.class);
        registration.registerBlockComponent(LongPowerComponentProvider.INSTANCE, LaserTransformerBlock.class);
        registration.registerBlockComponent(LongPowerComponentProvider.INSTANCE, LaserTransmissionAnchorBlock.class);
    }
}
