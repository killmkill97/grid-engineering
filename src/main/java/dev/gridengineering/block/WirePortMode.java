package dev.gridengineering.block;

import net.minecraft.util.StringRepresentable;

public enum WirePortMode implements StringRepresentable {
    AUTO("auto"),
    INPUT("input"),
    OUTPUT("output");

    private final String serializedName;

    WirePortMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public WirePortMode next() {
        return switch (this) {
            case AUTO -> INPUT;
            case INPUT -> OUTPUT;
            case OUTPUT -> AUTO;
        };
    }

    public String translationKey() {
        return "mode.gridengineering.wire_port." + this.serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
