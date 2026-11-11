package com.qualityclip.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Small reflection helpers. Everything returns null/false instead of throwing. */
public final class Reflect {
    private static final Pattern DEFAULT_TO_STRING = Pattern.compile(".*@[0-9a-fA-F]+$");

    private Reflect() {}

    public static Field field(Class<?> c, String name) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static List<Field> allFields(Class<?> c) {
        List<Field> out = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    out.add(f);
                } catch (Throwable ignored) {
                }
            }
        }
        return out;
    }

    public static Method method(Class<?> c, String name, Class<?>... params) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static Object get(Object o, String field) {
        try {
            Field f = field(o.getClass(), field);
            return f == null ? null : f.get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean call(Object o, String name) {
        try {
            Method m = method(o.getClass(), name);
            if (m == null) return false;
            m.invoke(o);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** best effort: turn "some page object" into its text; null if we can't tell */
    public static String textOf(Object o) {
        if (o == null) return "";
        if (o instanceof CharSequence cs) return cs.toString();
        String[] names = {"toLegacyString", "toLegacy", "toFormattedString", "getFormattedText", "serialize",
                "getRawText", "getText", "getString", "asString", "plainText", "text", "raw"};
        for (String n : names) {
            try {
                Method m = o.getClass().getMethod(n);
                if (m.getReturnType() == String.class) {
                    String s = (String) m.invoke(o);
                    if (s != null) return s;
                }
            } catch (Throwable ignored) {
            }
        }
        String s = String.valueOf(o);
        return DEFAULT_TO_STRING.matcher(s).matches() ? null : s;
    }

    /** human-readable dump for the log: class chain, fields, and what's inside list fields */
    public static String describe(Object o) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            sb.append("\n  class ").append(k.getName());
            for (Field f : k.getDeclaredFields()) {
                sb.append("\n    ").append(f.getType().getSimpleName()).append(' ').append(f.getName());
                try {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    if (v instanceof List<?> l) {
                        sb.append(" = List(").append(l.size()).append(')');
                        if (!l.isEmpty() && l.get(0) != null) sb.append(" of ").append(l.get(0).getClass().getName());
                    } else if (v instanceof Object[] a) {
                        sb.append(" = array(").append(a.length).append(')');
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return sb.toString();
    }
}
