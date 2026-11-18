package xyz.essencexc.astratest.java;

public enum PondMode {
    AUTOMATIC,
    NEAREST,
    MANUAL;

    @Override
    public String toString() {
        return this == MANUAL ? "Manual (pick in game)" : super.toString();
    }
}
