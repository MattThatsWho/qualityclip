package com.qualityclip.ui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Reminder time maths and friendly formatting. */
public final class Time {
    private Time() {}

    private static final ZoneId ZONE = ZoneId.systemDefault();

    public static LocalDateTime local(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZONE);
    }

    public static long toMs(LocalDateTime t) {
        return t.atZone(ZONE).toInstant().toEpochMilli();
    }

    public static String clock(LocalDateTime t, boolean h24) {
        return t.format(DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "h:mm a", Locale.getDefault()));
    }

    public static String clock(int hour, int minute, boolean h24) {
        return clock(LocalDateTime.of(2000, 1, 1, hour, minute), h24);
    }

    /** "Today 3:45 PM", "Tomorrow 8:00 AM", "Sat, Nov 7 - 3:45 PM" */
    public static String when(long ms, boolean h24) {
        LocalDateTime t = local(ms);
        LocalDate today = LocalDate.now(ZONE);
        String c = clock(t, h24);
        if (t.toLocalDate().equals(today)) return "Today " + c;
        if (t.toLocalDate().equals(today.plusDays(1))) return "Tomorrow " + c;
        return t.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())) + " " + c;
    }

    public static String dateLabel(int dayOffset) {
        if (dayOffset == 0) return "Today";
        if (dayOffset == 1) return "Tomorrow";
        return LocalDate.now(ZONE).plusDays(dayOffset)
                .format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()));
    }

    /** "in 12 min", "in 2 h 5 min", "in 3 days", "5 min ago" */
    public static String relative(long ms) {
        long diff = ms - System.currentTimeMillis();
        boolean past = diff < 0;
        long m = Math.abs(diff) / 60_000L;
        String s;
        if (m < 1) s = "less than a minute";
        else if (m < 60) s = m + " min";
        else if (m < 60 * 24) s = (m / 60) + " h" + (m % 60 == 0 ? "" : " " + (m % 60) + " min");
        else {
            long d = m / (60 * 24);
            s = d + (d == 1 ? " day" : " days");
        }
        if (m < 1) return past ? "just now" : "in under a minute";
        return past ? s + " ago" : "in " + s;
    }

    /** due time from the "in a while" controls */
    public static long inFromNow(int amount, int unit) {
        long perUnit = unit == 0 ? 60_000L : unit == 1 ? 3_600_000L : unit == 2 ? 86_400_000L : 1_200_000L; // a Minecraft day = 20 real minutes
        return System.currentTimeMillis() + amount * perUnit;
    }

    /** due time from the "at a time" controls */
    public static long at(int dayOffset, int hour, int minute) {
        return toMs(LocalDate.now(ZONE).plusDays(dayOffset).atTime(hour, minute));
    }

    public static long endOfToday() {
        return toMs(LocalDate.now(ZONE).plusDays(1).atStartOfDay());
    }
}
