package com.qualityclip.notify;

import com.qualityclip.compat.WorldInfo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Fills {x} {y} {z} {pos} {biome} {dimension} {time} {date} {mctime} {day} {player} in clip text. */
public final class Placeholders {
    public static final String HELP = "{x} {y} {z} {pos} {biome} {dimension} {time} {date} {mctime} {day} {player}";

    private Placeholders() {}

    public static String expand(String s) {
        if (s == null || s.indexOf('{') < 0) return s == null ? "" : s;
        String out = s;
        out = out.replace("{pos}", "{x} {y} {z}");
        out = rep(out, "{x}", WorldInfo::x);
        out = rep(out, "{y}", WorldInfo::y);
        out = rep(out, "{z}", WorldInfo::z);
        out = rep(out, "{biome}", WorldInfo::biome);
        out = rep(out, "{dimension}", WorldInfo::dimension);
        out = rep(out, "{mctime}", WorldInfo::mcTime);
        out = rep(out, "{day}", WorldInfo::mcDay);
        out = rep(out, "{player}", WorldInfo::playerName);
        out = rep(out, "{time}", () -> LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")));
        out = rep(out, "{date}", () -> LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
        return out;
    }

    public static boolean has(String s) {
        return s != null && s.indexOf('{') >= 0 && !expand(s).equals(s);
    }

    private static String rep(String s, String key, java.util.function.Supplier<String> v) {
        return s.contains(key) ? s.replace(key, v.get()) : s;
    }
}
