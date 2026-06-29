package dev.gridengineering.entanglement;

import net.minecraft.util.StringRepresentable;

/**
 * Runtime role for an Entanglement Link.
 *
 * <p>The block itself is the same block in both modes; this enum is stored in the
 * block state so the model can swap between the input and output textures.</p>
 */
public enum EntanglementLinkMode implements StringRepresentable {
    INPUT("input"),
    OUTPUT("output");

    private final String serializedName;

    EntanglementLinkMode(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Returns the mode used after pressing the GUI role button.
     */
    public EntanglementLinkMode next() {
        return this == INPUT ? OUTPUT : INPUT;
    }

    /**
     * Translation key used by the Entanglement Link screen.
     */
    public String translationKey() {
        return "mode.gridengineering.entanglement_link." + this.serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
