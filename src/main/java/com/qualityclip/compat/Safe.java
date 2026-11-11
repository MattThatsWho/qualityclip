package com.qualityclip.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.TextFieldHelper;

/** Everything here is best-effort. Callers treat any failure as "feature unavailable". */
public final class Safe {
    private Safe() {}

    public static String userName() {
        try {
            String n = Minecraft.getInstance().getUser().getName();
            if (n != null && !n.isBlank()) return n;
        } catch (Throwable ignored) {
        }
        return "Player";
    }

    public static boolean scribble() {
        try {
            return FabricLoader.getInstance().isModLoaded("scribble");
        } catch (Throwable t) {
            return false;
        }
    }

    /** open a folder in the operating system's file manager; false if that wasn't possible */
    public static boolean openFolder(java.nio.file.Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
            Class<?> util = Class.forName("net.minecraft.Util");
            Object platform = util.getMethod("getPlatform").invoke(null);
            for (java.lang.reflect.Method m : platform.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && (m.getName().equals("openPath") || m.getName().equals("openFile"))) {
                    Class<?> p = m.getParameterTypes()[0];
                    if (p.isAssignableFrom(java.nio.file.Path.class)) {
                        m.invoke(platform, dir);
                        return true;
                    }
                    if (p.isAssignableFrom(java.io.File.class)) {
                        m.invoke(platform, dir.toFile());
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** copy text to the system clipboard; false if this game version refused */
    public static boolean copy(String text) {
        try {
            TextFieldHelper.setClipboardContents(Minecraft.getInstance(), text);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** system clipboard text, or "" if unavailable */
    public static String clipboard() {
        try {
            String s = TextFieldHelper.getClipboardContents(Minecraft.getInstance());
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }
}
