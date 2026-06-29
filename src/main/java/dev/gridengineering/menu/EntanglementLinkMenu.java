package dev.gridengineering.menu;

import dev.gridengineering.block.entity.EntanglementLinkBlockEntity;
import dev.gridengineering.entanglement.EntanglementLinkMode;
import dev.gridengineering.entanglement.EntanglementLinkStatus;
import dev.gridengineering.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Menu for inserting an Entangled Electron and switching link role.
 */
public final class EntanglementLinkMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE_MODE = 0;
    public static final int DATA_COUNT = 8;
    private static final int LINK_SLOT = 0;
    private static final int PLAYER_SLOT_START = 1;

    private final Container container;
    private final ContainerData data;
    @Nullable
    private final EntanglementLinkBlockEntity link;

    public EntanglementLinkMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(1), new SimpleContainerData(DATA_COUNT), null);
    }

    public EntanglementLinkMenu(
            int containerId,
            Inventory inventory,
            EntanglementLinkBlockEntity link
    ) {
        this(containerId, inventory, link, link.createMenuData(), link);
    }

    private EntanglementLinkMenu(
            int containerId,
            Inventory inventory,
            Container container,
            ContainerData data,
            @Nullable EntanglementLinkBlockEntity link
    ) {
        super(ModContent.ENTANGLEMENT_LINK_MENU.get(), containerId);
        this.container = container;
        this.data = data;
        this.link = link;
        checkContainerSize(container, 1);
        checkContainerDataCount(data, DATA_COUNT);

        this.addSlot(new Slot(container, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModContent.ENTANGLED_ELECTRON.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        addPlayerInventory(inventory);
        this.addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_TOGGLE_MODE && this.link != null && this.stillValid(player)) {
            this.link.cycleMode();
            this.broadcastChanges();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == LINK_SLOT) {
            if (!this.moveItemStackTo(stack, PLAYER_SLOT_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModContent.ENTANGLED_ELECTRON.get())) {
            if (!this.moveItemStackTo(stack, LINK_SLOT, LINK_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    public EntanglementLinkMode getMode() {
        int index = this.data.get(0);
        EntanglementLinkMode[] modes = EntanglementLinkMode.values();
        return index >= 0 && index < modes.length ? modes[index] : EntanglementLinkMode.INPUT;
    }

    public EntanglementLinkStatus getStatus() {
        int index = this.data.get(1);
        EntanglementLinkStatus[] statuses = EntanglementLinkStatus.values();
        return index >= 0 && index < statuses.length
                ? statuses[index]
                : EntanglementLinkStatus.NO_ELECTRON;
    }

    public long getInputPower() {
        return combineLong(2);
    }

    public long getOutputPower() {
        return combineLong(4);
    }

    public long getLossPower() {
        return combineLong(6);
    }

    private long combineLong(int lowIndex) {
        return Integer.toUnsignedLong(this.data.get(lowIndex))
                | Integer.toUnsignedLong(this.data.get(lowIndex + 1)) << 32;
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }
}
