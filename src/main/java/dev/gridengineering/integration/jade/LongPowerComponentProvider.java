package dev.gridengineering.integration.jade;

import dev.gridengineering.GridEngineering;
import dev.gridengineering.block.LaserTransmissionAnchorBlock;
import dev.gridengineering.block.entity.CurrentRegulatorBlockEntity;
import dev.gridengineering.block.entity.LaserTransformerBlockEntity;
import dev.gridengineering.block.entity.TestBatteryBlockEntity;
import dev.gridengineering.energy.VoltageTiers;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;

public final class LongPowerComponentProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final LongPowerComponentProvider INSTANCE = new LongPowerComponentProvider();
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            GridEngineering.MOD_ID,
            "long_power"
    );
    private static final String TYPE_TAG = "GridEngineeringPowerType";
    private static final String MODE_TAG = "GridEngineeringPowerMode";
    private static final String ENERGY_TAG = "GridEngineeringStoredEnergy";
    private static final String VOLTAGE_TAG = "GridEngineeringVoltage";
    private static final String AMPS_TAG = "GridEngineeringAmps";
    private static final String MAX_OUTPUT_TAG = "GridEngineeringMaxOutput";
    private static final String INPUT_TAG = "GridEngineeringInput";
    private static final String OUTPUT_TAG = "GridEngineeringOutput";
    private static final String CONNECTED_TAG = "GridEngineeringConnected";
    private static final String TRANSMITTING_TAG = "GridEngineeringTransmitting";
    private static final String MAX_VOLTAGE_TAG = "GridEngineeringMaximumVoltage";
    private static final String ANCHORED_TAG = "GridEngineeringAnchored";
    private static final String ACTIVE_TAG = "GridEngineeringActive";

    private LongPowerComponentProvider() {
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof TestBatteryBlockEntity battery) {
            data.putString(TYPE_TAG, "battery");
            data.putString(MODE_TAG, battery.getMode().translationKey());
            data.putLong(ENERGY_TAG, battery.getStoredEnergyForDisplay());
            data.putLong(VOLTAGE_TAG, battery.getConfiguredVoltage());
            data.putInt(AMPS_TAG, battery.getConfiguredAmps());
            data.putLong(
                    MAX_OUTPUT_TAG,
                    Math.multiplyExact(
                            battery.getConfiguredVoltage(),
                            (long)battery.getConfiguredAmps()
                    )
            );
        } else if (accessor.getBlockEntity() instanceof CurrentRegulatorBlockEntity regulator) {
            data.putString(TYPE_TAG, "regulator");
            data.putLong(VOLTAGE_TAG, regulator.getConfiguredVoltage());
            data.putInt(AMPS_TAG, regulator.getConfiguredAmps());
            data.putLong(
                    MAX_OUTPUT_TAG,
                    Math.multiplyExact(
                            regulator.getConfiguredVoltage(),
                            (long)regulator.getConfiguredAmps()
                    )
            );
            data.putLong(INPUT_TAG, regulator.getInputPowerForDisplay());
            data.putLong(OUTPUT_TAG, regulator.getOutputPowerForDisplay());
        } else if (accessor.getBlockEntity() instanceof LaserTransformerBlockEntity laser) {
            data.putString(TYPE_TAG, "laser");
            data.putBoolean(CONNECTED_TAG, laser.linkedPos() != null);
            data.putBoolean(TRANSMITTING_TAG, laser.isTransmittingForDisplay());
            data.putLong(MAX_VOLTAGE_TAG, laser.getMaximumVoltageForDisplay());
            data.putBoolean(
                    ANCHORED_TAG,
                    LaserTransmissionAnchorBlock.isLaserAnchored(
                            accessor.getLevel(),
                            accessor.getPosition()
                    )
            );
        } else if (accessor.getBlockState().getBlock()
                instanceof LaserTransmissionAnchorBlock) {
            data.putString(TYPE_TAG, "laser_anchor");
            data.putBoolean(
                    ACTIVE_TAG,
                    accessor.getBlockState().getValue(LaserTransmissionAnchorBlock.ACTIVE)
            );
        }
    }

    @Override
    public void appendTooltip(
            ITooltip tooltip,
            BlockAccessor accessor,
            IPluginConfig config
    ) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(TYPE_TAG)) {
            return;
        }

        tooltip.remove(JadeIds.UNIVERSAL_ENERGY_STORAGE);
        if ("laser_anchor".equals(data.getString(TYPE_TAG))) {
            tooltip.add(Component.translatable(
                    "jade.gridengineering.anchor_status",
                    Component.translatable(data.getBoolean(ACTIVE_TAG)
                            ? "jade.gridengineering.status.operating"
                            : "jade.gridengineering.status.inactive")
            ));
            return;
        }

        if ("laser".equals(data.getString(TYPE_TAG))) {
            tooltip.add(Component.translatable(
                    "jade.gridengineering.connection",
                    Component.translatable(data.getBoolean(CONNECTED_TAG)
                            ? "jade.gridengineering.status.connected"
                            : "jade.gridengineering.status.disconnected")
            ));
            tooltip.add(Component.translatable(
                    "jade.gridengineering.transmitting",
                    Component.translatable(data.getBoolean(TRANSMITTING_TAG)
                            ? "jade.gridengineering.status.yes"
                            : "jade.gridengineering.status.no")
            ));
            long maximumVoltage = data.getLong(MAX_VOLTAGE_TAG);
            tooltip.add(Component.translatable(
                    "jade.gridengineering.maximum_voltage",
                    Component.literal(VoltageTiers.nameForVoltage(maximumVoltage))
            ));
            if (data.getBoolean(ANCHORED_TAG)) {
                tooltip.add(Component.translatable("jade.gridengineering.anchored"));
            }
            return;
        }

        if ("battery".equals(data.getString(TYPE_TAG))) {
            tooltip.add(Component.translatable(
                    "jade.gridengineering.mode",
                    Component.translatable(data.getString(MODE_TAG))
            ));
            tooltip.add(Component.translatable(
                    "jade.gridengineering.stored",
                    format(data.getLong(ENERGY_TAG))
            ));
        }

        tooltip.add(Component.translatable(
                "jade.gridengineering.voltage",
                format(data.getLong(VOLTAGE_TAG))
        ));
        tooltip.add(Component.translatable(
                "jade.gridengineering.amperage",
                format(data.getInt(AMPS_TAG))
        ));
        tooltip.add(Component.translatable(
                "jade.gridengineering.max_output",
                format(data.getLong(MAX_OUTPUT_TAG))
        ));

        if ("regulator".equals(data.getString(TYPE_TAG))) {
            tooltip.add(Component.translatable(
                    "jade.gridengineering.input",
                    format(data.getLong(INPUT_TAG))
            ));
            tooltip.add(Component.translatable(
                    "jade.gridengineering.output",
                    format(data.getLong(OUTPUT_TAG))
            ));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return 2_000;
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
