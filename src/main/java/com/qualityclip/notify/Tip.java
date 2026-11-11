package com.qualityclip.notify;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Minimal vanilla-looking tooltip (purple border, dark background). */
public final class Tip {
    private Tip() {}

    public static void draw(GuiGraphicsExtractor g, Font font, String text, int x, int y, int screenW) {
        int w = font.width(text) + 8;
        int h = 16;
        if (x + w > screenW - 2) x = screenW - w - 2;
        if (x < 2) x = 2;
        if (y < 2) y = 2;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF2A0A5E);
        g.fill(x, y, x + w, y + h, 0xF0100010);
        g.text(font, Component.literal(text), x + 4, y + 4, 0xFFFFFFFF);
    }
}
