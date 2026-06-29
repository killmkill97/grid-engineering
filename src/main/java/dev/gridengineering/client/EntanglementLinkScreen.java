package dev.gridengineering.client;

import dev.gridengineering.entanglement.EntanglementLinkMode;
import dev.gridengineering.menu.EntanglementLinkMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for the Entanglement Link.
 */
public final class EntanglementLinkScreen extends AbstractContainerScreen<EntanglementLinkMenu> {
    private Button modeButton;

    public EntanglementLinkScreen(EntanglementLinkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 8;
        this.titleLabelY = 8;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        this.modeButton = this.addRenderableWidget(
                Button.builder(Component.empty(), ignored -> clickServerButton(EntanglementLinkMenu.BUTTON_TOGGLE_MODE))
                        .bounds(this.leftPos + 48, this.topPos + 56, 80, 20)
                        .build()
        );
        updateModeButton();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updateModeButton();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(
                this.leftPos,
                this.topPos,
                this.leftPos + this.imageWidth,
                this.topPos + this.imageHeight,
                0xEE171A20
        );
        guiGraphics.fill(
                this.leftPos + 4,
                this.topPos + 4,
                this.leftPos + this.imageWidth - 4,
                this.topPos + this.imageHeight - 4,
                0xFF2B3038
        );
        guiGraphics.fill(this.leftPos + 79, this.topPos + 34, this.leftPos + 98, this.topPos + 53, 0xFF101218);
        guiGraphics.fill(this.leftPos + 80, this.topPos + 35, this.leftPos + 97, this.topPos + 52, 0xFF3A404A);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFF, false);
        guiGraphics.drawCenteredString(
                this.font,
                Component.translatable("screen.gridengineering.entanglement_link.role",
                        Component.translatable(this.menu.getMode().translationKey())),
                this.imageWidth / 2,
                19,
                0x70D8FF
        );
        guiGraphics.drawCenteredString(
                this.font,
                Component.translatable("screen.gridengineering.entanglement_link.status",
                        Component.translatable(this.menu.getStatus().translationKey())),
                this.imageWidth / 2,
                28,
                this.menu.getStatus().ordinal() == 4 ? 0x80FF9A : 0xFFE07A
        );
        guiGraphics.drawString(
                this.font,
                Component.translatable("screen.gridengineering.entanglement_link.input",
                        formatNumber(this.menu.getInputPower())),
                10,
                112,
                0x70D8FF,
                false
        );
        guiGraphics.drawString(
                this.font,
                Component.translatable("screen.gridengineering.entanglement_link.output",
                        formatNumber(this.menu.getOutputPower())),
                10,
                124,
                0x80FF9A,
                false
        );
        guiGraphics.drawString(
                this.font,
                Component.translatable("screen.gridengineering.entanglement_link.loss",
                        formatNumber(this.menu.getLossPower())),
                10,
                136,
                0xFF7070,
                false
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void updateModeButton() {
        if (this.modeButton == null) {
            return;
        }
        EntanglementLinkMode next = this.menu.getMode().next();
        this.modeButton.setMessage(
                Component.translatable(
                        "screen.gridengineering.entanglement_link.set_role",
                        Component.translatable(next.translationKey())
                )
        );
    }

    private void clickServerButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
