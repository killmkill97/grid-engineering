package dev.gridengineering.laser;

public enum LaserRole {
    SENDER("sender"),
    RECEIVER("receiver");

    private final String id;

    LaserRole(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
