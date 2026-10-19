package com.example.syncedn;

/** Fractional-pixel motion, independent of screen density and display refresh rate. */
public final class SheetScrollClock {
    private double position;
    public static int clampSpeed(int speed) { return Math.max(1, Math.min(10, speed)); }
    public void reset(int position) { this.position = Math.max(0, position); }
    public int advance(long elapsedMillis, int speed, float density, int maximum) {
        double seconds = Math.max(0L, Math.min(64L, elapsedMillis)) / 1000.0;
        position += seconds * clampSpeed(speed) * 8.0 * Math.max(0.1f, density);
        position = Math.max(0, Math.min(Math.max(0, maximum), position));
        return (int) position;
    }
}
