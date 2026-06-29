package dev.gridengineering.entanglement;

/**
 * Connection status for a UUID-paired Entanglement Link group.
 *
 * <p>The status is intentionally small and GUI-friendly. The manager computes it
 * from loaded links that contain Entangled Electron items with the same UUID.</p>
 */
public enum EntanglementLinkStatus {
    NO_ELECTRON("no_electron"),
    WAITING_FOR_PAIR("waiting_for_pair"),
    DUPLICATE_ELECTRONS("duplicate_electrons"),
    ROLE_CONFLICT("role_conflict"),
    CONNECTED("connected");

    private final String serializedName;

    EntanglementLinkStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Translation key used by screens and diagnostics.
     */
    public String translationKey() {
        return "status.gridengineering.entanglement_link." + this.serializedName;
    }
}
