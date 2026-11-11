package com.qualityclip.compat;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Minecraft 26.3.x adapter. 26.3 moved from GLFW to SDL: KeyEvent.key() is now an SDL scancode,
 * mouse buttons are SDL numbers (left = 1, right = 3), modifiers are SDL_KMOD masks.
 */
public final class Compat {
    public static final String TARGET = "26.3";
    // SDL scancodes
    public static final int K_ENTER = 40, K_ESC = 41, K_BACKSPACE = 42, K_DELETE = 76, K_KP_ENTER = 88,
            K_RIGHT = 79, K_LEFT = 80, K_DOWN = 81, K_UP = 82, K_PGUP = 75, K_PGDN = 78,
            K_HOME = 74, K_END = 77, K_V = 25, K_C = 6, K_A = 4, K_X = 27;
    private static final int SDL_SCANCODE_K = 14;

    private Compat() {}

    public static void setScreen(Screen s) {
        Minecraft.getInstance().gui.setScreen(s);
    }

    public static Screen screen() {
        return Minecraft.getInstance().gui.screen();
    }

    public static boolean isLeft(int button) {
        return button == 1;
    }

    public static boolean isRight(int button) {
        return button == 3;
    }

    public static boolean shift(int mods) {
        return (mods & 0x3) != 0;
    }

    /** SDL_KMOD_LCTRL | RCTRL = 0xC0, SDL_KMOD_LGUI | RGUI (Cmd on Mac) = 0xC00 */
    public static boolean ctrl(int mods) {
        return (mods & 0xC0) != 0 || (mods & 0xC00) != 0;
    }

    public static KeyMapping openKey(KeyMapping.Category category) {
        return key("key.qualityclip.open", 75, SDL_SCANCODE_K, category);
    }

    /**
     * KeyMapping's constructor changed with the SDL switch, so look it up instead of hard-linking.
     * Tries (String, int, Category) first, then (String, Type, int, Category). sdlKey 0 = unbound.
     */
    public static KeyMapping key(String name, int glfwKey, int sdlKey, KeyMapping.Category category) {
        try {
            for (Constructor<?> c : KeyMapping.class.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 3 && p[0] == String.class && p[1] == int.class && p[2] == KeyMapping.Category.class) {
                    return (KeyMapping) c.newInstance(name, sdlKey, category);
                }
            }
            for (Constructor<?> c : KeyMapping.class.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 4 && p[0] == String.class && p[2] == int.class && p[3] == KeyMapping.Category.class
                        && p[1].isEnum()) {
                    Object type = null;
                    for (Object k : p[1].getEnumConstants()) {
                        String n = k.toString();
                        if (n.equals("KEYBOARD") || n.equals("KEYSYM")) type = k;
                    }
                    if (type != null) return (KeyMapping) c.newInstance(name, type, sdlKey, category);
                }
            }
        } catch (Throwable ignored) {
        }
        return null; // no hotkey on this build; Mod Menu and the chat button still work
    }
}
