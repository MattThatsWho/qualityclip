package com.qualityclip.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reads and replaces the 4 text lines of an open sign editing screen (vanilla field names: messages, line, signField). */
public final class SignBridge {
    private SignBridge() {}

    private static Field messagesField(Object screen) {
        Field f = Reflect.field(screen.getClass(), "messages");
        if (f != null && f.getType() == String[].class) return f;
        for (Field x : Reflect.allFields(screen.getClass())) {
            try {
                if (x.getType() == String[].class && ((String[]) x.get(screen)).length == 4) return x;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** the 4 lines being edited, or null */
    public static String[] read(Object screen) {
        try {
            Field f = messagesField(screen);
            if (f == null) return null;
            String[] m = (String[]) f.get(screen);
            String[] out = new String[4];
            for (int i = 0; i < 4; i++) out[i] = i < m.length && m[i] != null ? m[i] : "";
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** REPLACES all four lines. */
    public static String paste(Object screen, String[] lines, boolean[] ok) {
        ok[0] = false;
        String fail = "This sign screen can't be filled automatically.";
        try {
            Field f = messagesField(screen);
            Object signField = Reflect.get(screen, "signField");
            Method toEnd = signField == null ? null : Reflect.method(signField.getClass(), "setCursorToEnd");
            if (f == null || toEnd == null) return fail;
            String[] m = (String[]) f.get(screen);
            for (int i = 0; i < m.length; i++) m[i] = i < lines.length && lines[i] != null ? lines[i] : "";
            Field line = Reflect.field(screen.getClass(), "line");
            if (line != null && line.getType() == int.class) line.setInt(screen, 0);
            toEnd.invoke(signField);
            ok[0] = true;
            return "Replaced the sign text!";
        } catch (Throwable t) {
            return fail;
        }
    }
}
