package dev.gridengineering.registry;

import dev.gridengineering.GridEngineering;
import dev.gridengineering.block.CurrentRegulatorBlock;
import dev.gridengineering.block.EntanglementLinkBlock;
import dev.gridengineering.block.LaserTransformerBlock;
import dev.gridengineering.block.LaserTransmissionAnchorBlock;
import dev.gridengineering.block.GridControllerBlock;
import dev.gridengineering.block.TestBatteryBlock;
import dev.gridengineering.block.WireBlock;
import dev.gridengineering.block.WireCoating;
import dev.gridengineering.block.WireGauge;
import dev.gridengineering.block.WireMaterial;
import dev.gridengineering.block.entity.CurrentRegulatorBlockEntity;
import dev.gridengineering.block.entity.EntanglementLinkBlockEntity;
import dev.gridengineering.block.entity.LaserTransformerBlockEntity;
import dev.gridengineering.block.entity.GridControllerBlockEntity;
import dev.gridengineering.block.entity.TestBatteryBlockEntity;
import dev.gridengineering.block.entity.WireBlockEntity;
import dev.gridengineering.item.EntangledElectronItem;
import dev.gridengineering.item.NetworkMonitorItem;
import dev.gridengineering.item.WireBlockItem;
import dev.gridengineering.item.WireCutterItem;
import dev.gridengineering.laser.LaserRole;
import dev.gridengineering.laser.LaserTier;
import dev.gridengineering.menu.EntanglementLinkMenu;
import dev.gridengineering.menu.PowerControlMenu;
import dev.gridengineering.gridcontroller.GridControllerTier;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GridEngineering.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GridEngineering.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, GridEngineering.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, GridEngineering.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GridEngineering.MOD_ID);
    private static final Map<String, DeferredBlock<WireBlock>> WIRE_BLOCKS = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<WireBlockItem>> WIRE_ITEMS = new LinkedHashMap<>();
    private static final Map<String, DeferredBlock<LaserTransformerBlock>> LASER_BLOCKS =
            new LinkedHashMap<>();
    private static final Map<String, DeferredItem<BlockItem>> LASER_ITEMS = new LinkedHashMap<>();
    private static final Map<String, DeferredBlock<GridControllerBlock>> GRID_CONTROLLER_BLOCKS =
            new LinkedHashMap<>();
    private static final Map<String, DeferredItem<BlockItem>> GRID_CONTROLLER_ITEMS =
            new LinkedHashMap<>();

    public static final DeferredBlock<WireBlock> COPPER_WIRE;
    public static final DeferredItem<WireBlockItem> COPPER_WIRE_ITEM;
    public static final DeferredBlock<WireBlock> TIN_WIRE;
    public static final DeferredItem<WireBlockItem> TIN_WIRE_ITEM;

    static {
        for (WireCoating coating : WireCoating.values()) {
            for (WireMaterial material : WireMaterial.values()) {
                for (WireGauge gauge : WireGauge.values()) {
                    registerWire(material, gauge, coating);
                }
            }
        }
        for (LaserTier tier : LaserTier.values()) {
            registerLaserTransformer(tier, LaserRole.SENDER);
            registerLaserTransformer(tier, LaserRole.RECEIVER);
        }
        for (GridControllerTier tier : GridControllerTier.values()) {
            registerGridController(tier);
        }

        COPPER_WIRE = WIRE_BLOCKS.get(wireId(WireMaterial.COPPER, WireGauge.MM_1));
        COPPER_WIRE_ITEM = WIRE_ITEMS.get(wireId(WireMaterial.COPPER, WireGauge.MM_1));
        TIN_WIRE = WIRE_BLOCKS.get(wireId(WireMaterial.TIN, WireGauge.MM_1));
        TIN_WIRE_ITEM = WIRE_ITEMS.get(wireId(WireMaterial.TIN, WireGauge.MM_1));
    }

    public static final DeferredItem<WireCutterItem> DIAMOND_WIRE_CUTTER = ITEMS.registerItem(
            "diamond_wire_cutter",
            WireCutterItem::new,
            new Item.Properties().durability(256).stacksTo(1)
    );

    public static final DeferredItem<WireCutterItem> IRON_WIRE_CUTTER = ITEMS.registerItem(
            "iron_wire_cutter",
            WireCutterItem::new,
            new Item.Properties().durability(128).stacksTo(1)
    );

    public static final DeferredItem<WireCutterItem> NTT_WIRE_CUTTER = ITEMS.registerItem(
            "ntt_alloy_wire_cutter",
            WireCutterItem::new,
            new Item.Properties().durability(2048).stacksTo(1)
    );

    public static final DeferredItem<WireCutterItem> NBB_WIRE_CUTTER = ITEMS.registerItem(
            "nbb_alloy_wire_cutter",
            properties -> new WireCutterItem(properties, false),
            new Item.Properties().stacksTo(1)
    );

    public static final DeferredItem<NetworkMonitorItem> NETWORK_MONITOR = ITEMS.registerItem(
            "network_monitor",
            NetworkMonitorItem::new,
            new Item.Properties().stacksTo(1)
    );

    public static final DeferredItem<Item> RUBBER =
            ITEMS.registerSimpleItem("rubber", new Item.Properties());

    public static final DeferredItem<Item> POLYETHYLENE =
            ITEMS.registerSimpleItem("polyethylene", new Item.Properties());

    public static final DeferredItem<Item> POLYPROPYLENE =
            ITEMS.registerSimpleItem("polypropylene", new Item.Properties());

    public static final DeferredItem<Item> POLYETHERETHERKETONE =
            ITEMS.registerSimpleItem("polyetheretherketone", new Item.Properties());

    public static final DeferredItem<EntangledElectronItem> ENTANGLED_ELECTRON = ITEMS.registerItem(
            "entangled_electron",
            EntangledElectronItem::new,
            new Item.Properties().stacksTo(2)
    );

    public static final DeferredBlock<TestBatteryBlock> TEST_BATTERY = BLOCKS.registerBlock(
            "test_battery",
            TestBatteryBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
    );

    public static final DeferredItem<BlockItem> TEST_BATTERY_ITEM =
            ITEMS.registerSimpleBlockItem("test_battery", TEST_BATTERY);

    public static final DeferredBlock<CurrentRegulatorBlock> CURRENT_REGULATOR = BLOCKS.registerBlock(
            "current_regulator",
            CurrentRegulatorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
    );

    public static final DeferredItem<BlockItem> CURRENT_REGULATOR_ITEM =
            ITEMS.registerSimpleBlockItem("current_regulator", CURRENT_REGULATOR);

    public static final DeferredBlock<EntanglementLinkBlock> ENTANGLEMENT_LINK = BLOCKS.registerBlock(
            "entanglement_link",
            EntanglementLinkBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 8.0F)
                    .sound(SoundType.METAL)
    );

    public static final DeferredItem<BlockItem> ENTANGLEMENT_LINK_ITEM =
            ITEMS.registerSimpleBlockItem("entanglement_link", ENTANGLEMENT_LINK);

    public static final DeferredBlock<LaserTransmissionAnchorBlock> LASER_TRANSMISSION_ANCHOR =
            BLOCKS.registerBlock(
                    "laser_transmission_anchor",
                    LaserTransmissionAnchorBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(4.0F, 8.0F)
                            .sound(SoundType.STONE)
                            .noOcclusion()
            );

    public static final DeferredItem<BlockItem> LASER_TRANSMISSION_ANCHOR_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "laser_transmission_anchor",
                    LASER_TRANSMISSION_ANCHOR
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WireBlockEntity>> WIRE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "wire",
                    () -> BlockEntityType.Builder.of(
                            WireBlockEntity::new,
                            WIRE_BLOCKS.values().stream()
                                    .map(DeferredBlock::get)
                                    .toArray(Block[]::new)
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TestBatteryBlockEntity>>
            TEST_BATTERY_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
                    "test_battery",
                    () -> BlockEntityType.Builder.of(TestBatteryBlockEntity::new, TEST_BATTERY.get()).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CurrentRegulatorBlockEntity>>
            CURRENT_REGULATOR_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
                    "current_regulator",
                    () -> BlockEntityType.Builder.of(
                            CurrentRegulatorBlockEntity::new,
                            CURRENT_REGULATOR.get()
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EntanglementLinkBlockEntity>>
            ENTANGLEMENT_LINK_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
                    "entanglement_link",
                    () -> BlockEntityType.Builder.of(
                            EntanglementLinkBlockEntity::new,
                            ENTANGLEMENT_LINK.get()
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaserTransformerBlockEntity>>
            LASER_TRANSFORMER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
                    "laser_transformer",
                    () -> BlockEntityType.Builder.of(
                            LaserTransformerBlockEntity::new,
                            LASER_BLOCKS.values().stream()
                                    .map(DeferredBlock::get)
                                    .toArray(Block[]::new)
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GridControllerBlockEntity>>
            GRID_CONTROLLER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
                    "grid_controller",
                    () -> BlockEntityType.Builder.of(
                            GridControllerBlockEntity::new,
                            GRID_CONTROLLER_BLOCKS.values().stream()
                                    .map(DeferredBlock::get)
                                    .toArray(Block[]::new)
                    ).build(null)
            );

    public static final DeferredHolder<MenuType<?>, MenuType<PowerControlMenu>> POWER_CONTROL_MENU =
            MENU_TYPES.register(
                    "power_control",
                    () -> new MenuType<>(PowerControlMenu::new, FeatureFlags.DEFAULT_FLAGS)
            );

    public static final DeferredHolder<MenuType<?>, MenuType<EntanglementLinkMenu>> ENTANGLEMENT_LINK_MENU =
            MENU_TYPES.register(
                    "entanglement_link",
                    () -> new MenuType<>(EntanglementLinkMenu::new, FeatureFlags.DEFAULT_FLAGS)
            );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GRID_ENGINEERING_TAB =
            CREATIVE_MODE_TABS.register(
                    "grid_engineering",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.gridengineering"))
                            .icon(() -> new ItemStack(NETWORK_MONITOR.get()))
                            .displayItems((parameters, output) -> {
                                output.accept(DIAMOND_WIRE_CUTTER.get());
                                output.accept(IRON_WIRE_CUTTER.get());
                                output.accept(NTT_WIRE_CUTTER.get());
                                output.accept(NBB_WIRE_CUTTER.get());
                                output.accept(NETWORK_MONITOR.get());
                                output.accept(RUBBER.get());
                                output.accept(POLYETHYLENE.get());
                                output.accept(POLYPROPYLENE.get());
                                output.accept(POLYETHERETHERKETONE.get());
                                output.accept(ENTANGLED_ELECTRON.get());
                                output.accept(TEST_BATTERY_ITEM.get());
                                output.accept(CURRENT_REGULATOR_ITEM.get());
                                output.accept(ENTANGLEMENT_LINK_ITEM.get());
                                output.accept(LASER_TRANSMISSION_ANCHOR_ITEM.get());
                                gridControllerItems().forEach(output::accept);
                                laserItems().forEach(output::accept);
                                wireItems().forEach(output::accept);
                            })
                            .build()
            );

    private ModContent() {
    }

    public static Collection<DeferredBlock<WireBlock>> wireBlocks() {
        return WIRE_BLOCKS.values();
    }

    public static Collection<DeferredItem<WireBlockItem>> wireItems() {
        return WIRE_ITEMS.values();
    }

    public static Collection<DeferredBlock<LaserTransformerBlock>> laserBlocks() {
        return LASER_BLOCKS.values();
    }

    public static Collection<DeferredItem<BlockItem>> laserItems() {
        return LASER_ITEMS.values();
    }

    public static Collection<DeferredBlock<GridControllerBlock>> gridControllerBlocks() {
        return GRID_CONTROLLER_BLOCKS.values();
    }

    public static Collection<DeferredItem<BlockItem>> gridControllerItems() {
        return GRID_CONTROLLER_ITEMS.values();
    }

    public static String wireId(WireMaterial material, WireGauge gauge) {
        return wireId(material, gauge, WireCoating.BARE);
    }

    public static String wireId(WireMaterial material, WireGauge gauge, WireCoating coating) {
        return coating.idPrefix() + material.id() + "_wire" + gauge.suffix();
    }

    private static void registerWire(
            WireMaterial material,
            WireGauge gauge,
            WireCoating coating
    ) {
        String id = wireId(material, gauge, coating);
        DeferredBlock<WireBlock> block = BLOCKS.registerBlock(
                id,
                properties -> new WireBlock(properties, material, gauge, coating),
                BlockBehaviour.Properties.of()
                        .mapColor(material == WireMaterial.COPPER
                                ? MapColor.COLOR_ORANGE
                                : MapColor.METAL)
                        .strength(0.3F)
                        .sound(SoundType.COPPER)
                        .noOcclusion()
        );
        DeferredItem<WireBlockItem> item = ITEMS.register(
                id,
                () -> new WireBlockItem(block.get(), new Item.Properties())
        );
        WIRE_BLOCKS.put(id, block);
        WIRE_ITEMS.put(id, item);
    }

    public static String laserId(LaserTier tier, LaserRole role) {
        return "laser_" + role.id() + "_" + tier.id();
    }

    private static void registerLaserTransformer(LaserTier tier, LaserRole role) {
        String id = laserId(tier, role);
        DeferredBlock<LaserTransformerBlock> block = BLOCKS.registerBlock(
                id,
                properties -> new LaserTransformerBlock(properties, tier, role),
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(4.0F, 8.0F)
                        .sound(SoundType.METAL)
        );
        DeferredItem<BlockItem> item = ITEMS.registerSimpleBlockItem(id, block);
        LASER_BLOCKS.put(id, block);
        LASER_ITEMS.put(id, item);
    }

    public static String gridControllerId(GridControllerTier tier) {
        return "grid_controller_" + tier.id();
    }

    private static void registerGridController(GridControllerTier tier) {
        String id = gridControllerId(tier);
        DeferredBlock<GridControllerBlock> block = BLOCKS.registerBlock(
                id,
                properties -> new GridControllerBlock(properties, tier),
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(4.0F, 8.0F)
                        .sound(SoundType.METAL)
        );
        DeferredItem<BlockItem> item = ITEMS.registerSimpleBlockItem(id, block);
        GRID_CONTROLLER_BLOCKS.put(id, block);
        GRID_CONTROLLER_ITEMS.put(id, item);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(ModContent::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                TEST_BATTERY_BLOCK_ENTITY.get(),
                (battery, side) -> battery.getEnergyStorage()
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                CURRENT_REGULATOR_BLOCK_ENTITY.get(),
                CurrentRegulatorBlockEntity::getEnergyStorage
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ENTANGLEMENT_LINK_BLOCK_ENTITY.get(),
                EntanglementLinkBlockEntity::getEnergyStorage
        );
    }
}
