package com.qualityclip.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Colours and small drawing helpers shared by every QualityClip screen. */
public final class Theme {
    public static final int DIM = 0xC00A0E12;
    public static final int BORDER = 0xFF05080B;
    public static final int PANEL = 0xFF1A2027;
    public static final int PANEL_HI = 0xFF232C35;
    public static final int PANEL_LO = 0xFF141A20;
    public static final int LINE = 0xFF2C3742;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFFA3AFBC;
    public static final int FAINT = 0xFF6B7885;
    public static final int GOLD = 0xFFFFC857;
    public static final int TEAL = 0xFF4FD1C5;
    public static final int GREEN = 0xFF5BE38A;
    public static final int RED = 0xFFFF6B6B;
    public static final int BLUE = 0xFF6CB4FF;

    private Theme() {}

    public static void rr(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x + 1, y, x + w - 1, y + h, c);
        g.fill(x, y + 1, x + w, y + h - 1, c);
    }

    /** raised panel with a dark outline and a lit top edge */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        rr(g, x - 1, y - 1, w + 2, h + 2, BORDER);
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, 0xFF2F3B47);
    }

    /** sunken area */
    public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
        g.fill(x, y, x + w, y + h, PANEL_LO);
    }

    public static void line(GuiGraphicsExtractor g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, LINE);
    }

    public static int mix(int a, int b, double f) {
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int gg = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }

    public static String fit(Font font, String s, int maxW) {
        if (maxW <= 0 || s == null) return "";
        if (font.width(s) <= maxW) return s;
        String cut = s;
        while (cut.length() > 1 && font.width(cut + "...") > maxW) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
    }
}
