package com.qualityclip.data;

/** A reminder attached to a clip. Uses wall-clock time so it survives quitting the game. */
public final class Schedule {
    public enum Mode { ONCE, TODAY, FOREVER }

    public Mode mode = Mode.ONCE;
    public int intervalMinutes = 15;
    public boolean sound = true;
    /** epoch millis of the next reminder */
    public long nextFireMs;
    /** epoch millis when a TODAY schedule stops (0 = never) */
    public long endMs;
}
