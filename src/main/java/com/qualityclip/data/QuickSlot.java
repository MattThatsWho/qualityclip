package com.qualityclip.data;

import java.util.ArrayList;
import java.util.List;

/** One Quick Clip: a template with placeholders, and what to do with the result. */
public final class QuickSlot {
    public static final int SAVE = 0, COPY = 1, BOTH = 2;
    public static final int COUNT = 4;

    public String name = "Quick Clip";
    public String template = "";
    public int mode = BOTH;

    public QuickSlot() {}

    public QuickSlot(String name, String template, int mode) {
        this.name = name;
        this.template = template;
        this.mode = mode;
    }

    public static List<QuickSlot> defaults() {
        List<QuickSlot> l = new ArrayList<>();
        l.add(new QuickSlot("Coordinates", "{x}, {y}, {z}", BOTH));
        l.add(new QuickSlot("Location", "{x} {y} {z} in the {biome} ({dimension})", BOTH));
        l.add(new QuickSlot("Time", "{time} - Minecraft day {day}", COPY));
        l.add(new QuickSlot("Custom", "", BOTH));
        return l;
    }

    public static List<QuickSlot> fix(List<QuickSlot> in) {
        List<QuickSlot> out = new ArrayList<>();
        List<QuickSlot> def = defaults();
        for (int i = 0; i < COUNT; i++) {
            QuickSlot s = in != null && i < in.size() ? in.get(i) : null;
            if (s == null) s = def.get(i);
            if (s.name == null) s.name = "Quick Clip";
            if (s.template == null) s.template = "";
            if (s.mode < 0 || s.mode > 2) s.mode = BOTH;
            out.add(s);
        }
        return out;
    }
}
