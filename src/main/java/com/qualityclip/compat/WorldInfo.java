package com.qualityclip.compat;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player position, biome, dimension and world time, read by name through reflection so a game update can only
 * ever turn a value into "?", never crash. All methods return "?" when something isn't available.
 */
public final class WorldInfo {
    private WorldInfo() {}

    private static Object call(Object o, String name, Object... args) throws Exception {
        for (Method m : o.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                try {
                    return m.invoke(o, args);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Object player() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        Field f = mc.getClass().getField("player");
        return f.get(mc);
    }

    public static boolean inWorld() {
        try {
            return player() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String coord(String axis) {
        try {
            Object p = player();
            if (p == null) return "?";
            double v = (Double) call(p, axis);
            return String.valueOf((int) Math.floor(v));
        } catch (Throwable t) {
            return "?";
        }
    }

    public static String x() {
        return coord("getX");
    }

    public static String y() {
        return coord("getY");
    }

    public static String z() {
        return coord("getZ");
    }

    public static String playerName() {
        return Safe.userName();
    }

    private static final Pattern KEY = Pattern.compile("([a-z0-9_.-]+:)?([a-z0-9_./-]+)\\]?\\s*$");

    private static String pretty(String raw) {
        Matcher m = KEY.matcher(raw.trim());
        String id = m.find() ? m.group(2) : raw;
        int slash = id.lastIndexOf('/');
        if (slash >= 0) id = id.substring(slash + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    public static String biome() {
        try {
            Object p = player();
            if (p == null) return "?";
            Object level = call(p, "level");
            Object pos = call(p, "blockPosition");
            Object holder = call(level, "getBiome", pos);
            Object opt = call(holder, "unwrapKey");
            if (opt instanceof Optional<?> o && o.isPresent()) return pretty(o.get().toString());
            return "?";
        } catch (Throwable t) {
            return "?";
        }
    }

    public static String dimension() {
        try {
            Object p = player();
            if (p == null) return "?";
            Object level = call(p, "level");
            return pretty(call(level, "dimension").toString());
        } catch (Throwable t) {
            return "?";
        }
    }

    private static long dayTime() throws Exception {
        Object level = call(player(), "level");
        return (Long) call(level, "getDayTime");
    }

    /** in-game clock like 14:30 */
    public static String mcTime() {
        try {
            long t = dayTime() % 24000L;
            long mins = (t * 60L / 1000L + 6 * 60) % (24 * 60);
            return String.format("%02d:%02d", mins / 60, mins % 60);
        } catch (Throwable t) {
            return "?";
        }
    }

    public static String mcDay() {
        try {
            return String.valueOf(dayTime() / 24000L);
        } catch (Throwable t) {
            return "?";
        }
    }
}
