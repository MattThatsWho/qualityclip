package com.qualityclip.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Minecraft 26.2.x adapter: still GLFW key codes, but the screen API moved from Minecraft to Gui. */
public final class Compat {
    public static final String TARGET = "26.2";
    public static final int K_ESC = 256, K_ENTER = 257, K_BACKSPACE = 259, K_DELETE = 261, K_KP_ENTER = 335,
            K_RIGHT = 262, K_LEFT = 263, K_DOWN = 264, K_UP = 265, K_PGUP = 266, K_PGDN = 267,
            K_HOME = 268, K_END = 269, K_V = 86, K_C = 67, K_A = 65, K_X = 88;

    private Compat() {}

    public static void setScreen(Screen s) {
        Minecraft.getInstance().gui.setScreen(s);
    }

    public static Screen screen() {
        return Minecraft.getInstance().gui.screen();
    }

    public static boolean isLeft(int button) {
        return button == 0;
    }

    public static boolean isRight(int button) {
        return button == 1;
    }

    public static boolean shift(int mods) {
        return (mods & 1) != 0;
    }

    public static boolean ctrl(int mods) {
        return (mods & 2) != 0 || (mods & 8) != 0;
    }

    public static KeyMapping openKey(KeyMapping.Category category) {
        return key("key.qualityclip.open", 75 /* K */, 0, category);
    }

    /** glfwKey = default key (-1 = unbound); sdlKey is only used on 26.3 */
    public static KeyMapping key(String name, int glfwKey, int sdlKey, KeyMapping.Category category) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, glfwKey, category);
    }
}
