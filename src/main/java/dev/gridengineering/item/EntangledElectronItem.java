package dev.gridengineering.item;

import dev.gridengineering.registry.ModContent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Pairing item for Entanglement Links.
 *
 * <p>Two electrons with the same UUID form one pair. The creative tab provides a
 * two-item stack with one UUID so players can split it into a matched pair.</p>
 */
public final class EntangledElectronItem extends Item {
    private static final String UUID_TAG = "GridEngineeringEntanglementId";

    public EntangledElectronItem(Properties properties) {
        super(properties);
    }

    /**
     * Creates a two-electron stack that shares one UUID.
     */
    public static ItemStack newPairStack() {
        ItemStack stack = new ItemStack(ModContent.ENTANGLED_ELECTRON.get(), 2);
        setEntanglementId(stack, UUID.randomUUID());
        return stack;
    }

    /**
     * Reads the electron UUID without mutating the stack.
     */
    public static Optional<UUID> entanglementId(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof EntangledElectronItem)) {
            return Optional.empty();
        }

        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        return tag.hasUUID(UUID_TAG) ? Optional.of(tag.getUUID(UUID_TAG)) : Optional.empty();
    }

    /**
     * Ensures the stack has a UUID and returns it.
     */
    public static Optional<UUID> getOrCreateEntanglementId(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof EntangledElectronItem)) {
            return Optional.empty();
        }

        Optional<UUID> existing = entanglementId(stack);
        if (existing.isPresent()) {
            return existing;
        }

        UUID created = UUID.randomUUID();
        setEntanglementId(stack, created);
        return Optional.of(created);
    }

    /**
     * Writes the UUID into the stack's custom data component.
     */
    public static void setEntanglementId(ItemStack stack, UUID id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(UUID_TAG, id));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide) {
            getOrCreateEntanglementId(stack);
        }
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        tooltipComponents.add(
                Component.translatable("tooltip.gridengineering.entangled_electron.use")
                        .withStyle(ChatFormatting.GRAY)
        );
        entanglementId(stack).ifPresentOrElse(
                id -> tooltipComponents.add(
                        Component.translatable(
                                        "tooltip.gridengineering.entangled_electron.uuid",
                                        id.toString()
                                )
                                .withStyle(ChatFormatting.DARK_AQUA)
                ),
                () -> tooltipComponents.add(
                        Component.translatable("tooltip.gridengineering.entangled_electron.unassigned")
                                .withStyle(ChatFormatting.DARK_GRAY)
                )
        );
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return entanglementId(stack).isPresent() || super.isFoil(stack);
    }
}
