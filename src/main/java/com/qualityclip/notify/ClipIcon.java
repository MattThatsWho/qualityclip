package com.qualityclip.notify;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A tiny clipboard drawn with rectangles (12 x 14 px). Placeholder-friendly: no texture needed. */
public final class ClipIcon {
    private ClipIcon() {}

    /** integer-scaled copy of the icon (scale 1 = 12x14 px) */
    public static void drawScaled(GuiGraphicsExtractor g, int x, int y, int s) {
        int[][] r = {
                {0, 2, 12, 14, 0xFF5A3A1A}, {1, 3, 11, 13, 0xFFA0702F}, {2, 4, 10, 12, 0xFFF4F1E8},
                {3, 6, 9, 7, 0xFF777777}, {3, 8, 9, 9, 0xFF777777}, {3, 10, 7, 11, 0xFF777777},
                {3, 0, 9, 4, 0xFF9A9A9A}, {4, 1, 8, 3, 0xFF4A4A4A}};
        for (int[] q : r) g.fill(x + q[0] * s, y + q[1] * s, x + q[2] * s, y + q[3] * s, q[4]);
    }

    public static void draw(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y + 2, x + 12, y + 14, 0xFF5A3A1A);          // board outline
        g.fill(x + 1, y + 3, x + 11, y + 13, 0xFFA0702F);      // board
        g.fill(x + 2, y + 4, x + 10, y + 12, 0xFFF4F1E8);      // paper
        g.fill(x + 3, y + 6, x + 9, y + 7, 0xFF777777);        // text lines
        g.fill(x + 3, y + 8, x + 9, y + 9, 0xFF777777);
        g.fill(x + 3, y + 10, x + 7, y + 11, 0xFF777777);
        g.fill(x + 3, y, x + 9, y + 4, 0xFF9A9A9A);            // metal clip
        g.fill(x + 4, y + 1, x + 8, y + 3, 0xFF4A4A4A);
    }
}
