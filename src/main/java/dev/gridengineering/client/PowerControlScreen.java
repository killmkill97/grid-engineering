package dev.gridengineering.client;

import dev.gridengineering.menu.PowerControlMenu;
import dev.gridengineering.menu.PowerControlTarget;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public final class PowerControlScreen extends AbstractContainerScreen<PowerControlMenu> {
    private Button modeButton;
    private EditBox voltageInput;
    private EditBox amperageInput;

    public PowerControlScreen(PowerControlMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 232;
        this.imageHeight = 166;
        this.titleLabelX = 12;
        this.titleLabelY = 10;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.leftPos + this.imageWidth / 2;

        this.addRenderableWidget(button("-", centerX - 82, this.topPos + 48, 32, PowerControlMenu.BUTTON_VOLTAGE_DOWN));
        this.addRenderableWidget(button("+", centerX + 50, this.topPos + 48, 32, PowerControlMenu.BUTTON_VOLTAGE_UP));

        this.voltageInput = new EditBox(
                this.font,
                centerX - 46,
                this.topPos + 48,
                92,
                20,
                Component.translatable("screen.gridengineering.power_control.voltage_input")
        );
        this.voltageInput.setMaxLength(18);
        this.voltageInput.setFilter(
                value -> value.isEmpty() || value.chars().allMatch(Character::isDigit)
        );
        this.voltageInput.setValue(Long.toString(this.menu.getVoltage()));
        this.addRenderableWidget(this.voltageInput);

        this.addRenderableWidget(button("-4", centerX - 110, this.topPos + 94, 32, PowerControlMenu.BUTTON_AMPS_DOWN_FAST));
        this.addRenderableWidget(button("-1", centerX - 74, this.topPos + 94, 32, PowerControlMenu.BUTTON_AMPS_DOWN));

        this.amperageInput = new EditBox(
                this.font,
                centerX - 36,
                this.topPos + 94,
                72,
                20,
                Component.translatable("screen.gridengineering.power_control.amperage_input")
        );
        this.amperageInput.setMaxLength(20);
        this.amperageInput.setFilter(PowerControlScreen::isSignedInteger);
        this.amperageInput.setValue(Integer.toString(this.menu.getAmps()));
        this.addRenderableWidget(this.amperageInput);

        this.addRenderableWidget(button("+1", centerX + 42, this.topPos + 94, 32, PowerControlMenu.BUTTON_AMPS_UP));
        this.addRenderableWidget(button("+4", centerX + 78, this.topPos + 94, 32, PowerControlMenu.BUTTON_AMPS_UP_FAST));

        this.modeButton = this.addRenderableWidget(
                Button.builder(Component.empty(), ignored -> clickServerButton(PowerControlMenu.BUTTON_MODE))
                        .bounds(centerX - 60, this.topPos + 132, 120, 20)
                        .build()
        );
        this.updateModeButton();
        this.updateVoltageInput();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.updateModeButton();
        this.updateVoltageInput();
        if (this.voltageInput != null
                && this.voltageInput.visible
                && !this.voltageInput.isFocused()) {
            String synchronizedVoltage = Long.toString(this.menu.getVoltage());
            if (!this.voltageInput.getValue().equals(synchronizedVoltage)) {
                this.voltageInput.setValue(synchronizedVoltage);
            }
        }
        if (this.amperageInput != null
                && this.amperageInput.visible
                && !this.amperageInput.isFocused()) {
            String synchronizedAmperage = Integer.toString(this.menu.getAmps());
            if (!this.amperageInput.getValue().equals(synchronizedAmperage)) {
                this.amperageInput.setValue(synchronizedAmperage);
            }
        }
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
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFF, false);

        Component voltage = Component.translatable(
                "screen.gridengineering.power_control.voltage",
                this.menu.getVoltageTierName(),
                formatNumber(this.menu.getVoltage())
        );
        guiGraphics.drawCenteredString(this.font, voltage, this.imageWidth / 2, 34, 0xFFE07A);
        if (this.menu.getControlMode() == PowerControlTarget.REGULATOR_MODE) {
            guiGraphics.drawCenteredString(
                    this.font,
                    Component.translatable("screen.gridengineering.power_control.voltage_input_hint"),
                    this.imageWidth / 2,
                    69,
                    0xAEB7C4
            );
        }

        Component amperage = Component.translatable(
                "screen.gridengineering.power_control.amperage",
                this.menu.getAmps()
        );
        guiGraphics.drawCenteredString(this.font, amperage, this.imageWidth / 2, 80, 0x70D8FF);

        long output = Math.multiplyExact(this.menu.getVoltage(), (long)this.menu.getAmps());
        Component throughput = Component.translatable(
                "screen.gridengineering.power_control.output",
                formatNumber(output)
        );
        guiGraphics.drawCenteredString(this.font, throughput, this.imageWidth / 2, 116, 0x80FF9A);

        if (this.menu.getControlMode() == PowerControlTarget.REGULATOR_MODE) {
            Component input = Component.translatable(
                    "screen.gridengineering.power_control.input_actual",
                    formatNumber(this.menu.getInputPower())
            );
            Component outputActual = Component.translatable(
                    "screen.gridengineering.power_control.output_actual",
                    formatNumber(this.menu.getOutputPower())
            );
            guiGraphics.drawString(this.font, input, 10, 142, 0x70D8FF, false);
            guiGraphics.drawString(this.font, outputActual, 10, 154, 0x80FF9A, false);
        } else if (this.menu.getControlMode() != 1) {
            Component stored = Component.translatable(
                    "screen.gridengineering.power_control.stored",
                    formatNumber(this.menu.getStoredEnergy())
            );
            guiGraphics.drawString(this.font, stored, 10, 154, 0xAEB7C4, false);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.voltageInput != null
                && this.voltageInput.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            this.applyVoltageInput();
            return true;
        }
        if (this.amperageInput != null
                && this.amperageInput.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            this.applyAmperageInput();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private Button button(String label, int x, int y, int width, int id) {
        return Button.builder(Component.literal(label), ignored -> clickServerButton(id))
                .bounds(x, y, width, 20)
                .build();
    }

    private void clickServerButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void applyVoltageInput() {
        if (this.voltageInput == null || this.minecraft == null || this.minecraft.gameMode == null) {
            return;
        }

        String value = this.voltageInput.getValue();
        if (value.isEmpty()) {
            return;
        }

        clickServerButton(PowerControlMenu.BUTTON_VOLTAGE_INPUT_CLEAR);
        for (int index = 0; index < value.length(); index++) {
            clickServerButton(
                    PowerControlMenu.BUTTON_VOLTAGE_DIGIT_BASE + value.charAt(index) - '0'
            );
        }
        clickServerButton(PowerControlMenu.BUTTON_VOLTAGE_INPUT_APPLY);
        this.voltageInput.setFocused(false);
    }

    private void applyAmperageInput() {
        if (this.amperageInput == null || this.minecraft == null || this.minecraft.gameMode == null) {
            return;
        }

        String value = this.amperageInput.getValue();
        if (value.isEmpty() || value.equals("-")) {
            return;
        }

        clickServerButton(PowerControlMenu.BUTTON_AMPS_INPUT_CLEAR);
        int digitStart = 0;
        if (value.charAt(0) == '-') {
            clickServerButton(PowerControlMenu.BUTTON_AMPS_INPUT_NEGATIVE);
            digitStart = 1;
        }
        for (int index = digitStart; index < value.length(); index++) {
            clickServerButton(
                    PowerControlMenu.BUTTON_AMPS_DIGIT_BASE + value.charAt(index) - '0'
            );
        }
        clickServerButton(PowerControlMenu.BUTTON_AMPS_INPUT_APPLY);
        this.amperageInput.setFocused(false);
    }

    private void updateModeButton() {
        if (this.modeButton == null) {
            return;
        }

        int mode = this.menu.getControlMode();
        this.modeButton.visible = mode != PowerControlTarget.REGULATOR_MODE;
        this.modeButton.active = this.modeButton.visible;
        this.modeButton.setMessage(switch (mode) {
            case 0 -> Component.translatable("mode.gridengineering.test_battery.sink");
            case 1 -> Component.translatable("mode.gridengineering.test_battery.source");
            case 2 -> Component.translatable("mode.gridengineering.test_battery.buffer");
            case 3 -> Component.translatable("mode.gridengineering.test_battery.trash");
            default -> Component.empty();
        });
    }

    private void updateVoltageInput() {
        if (this.voltageInput == null) {
            return;
        }

        this.voltageInput.visible = true;
        this.voltageInput.active = true;
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static boolean isSignedInteger(String value) {
        if (value.isEmpty() || value.equals("-")) {
            return true;
        }
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) {
            return false;
        }
        for (int index = start; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}
