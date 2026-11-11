package com.qualityclip.notify;

import com.qualityclip.data.Settings.NotifyStyle;
import com.qualityclip.ui.Icons;
import com.qualityclip.ui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Draws the three reminder looks. Only fill() and text() are used. kind: 0 = chat clip, 1 = notes, 2 = reminder. */
public final class Toasts {
    public static final int TEXT = 0, BOOK = 1, REMINDER = 2, SIGN = 3;

    private Toasts() {}

    public static int width(NotifyStyle st, int screenW) {
        return switch (st) {
            case STANDARD -> 160;
            case BOLD -> 224;
            case SUPER -> Math.max(200, Math.min(340, screenW - 24));
        };
    }

    public static int height(NotifyStyle st) {
        return switch (st) {
            case STANDARD -> 32;
            case BOLD -> 46;
            case SUPER -> 62;
        };
    }

    private static void rr(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        Theme.rr(g, x, y, w, h, c);
    }

    static int mix(int a, int b, double f) {
        return Theme.mix(a, b, f);
    }

    private static Component bold(String s) {
        return Component.literal(s).withStyle(st -> st.withBold(Boolean.TRUE));
    }

    static String fit(Font font, String s, int maxW) {
        return Theme.fit(font, s, maxW);
    }

    public static String message(Font font, String name, int kind, int maxW) {
        if (kind == REMINDER) return fit(font, name, maxW);
        String pre = kind == BOOK || kind == SIGN ? "Open '" : "Send '";
        String post = "' now!";
        return pre + fit(font, name, maxW - font.width(pre) - font.width(post)) + post;
    }

    private static String title(NotifyStyle st, int kind, int count) {
        String base;
        switch (st) {
            case STANDARD -> base = kind == REMINDER ? "Reminder!" : "Clip Reminder!";
            case BOLD -> base = kind == REMINDER ? "REMINDER" : "CLIP REMINDER";
            default -> base = kind == REMINDER ? "!! REMINDER !!" : "!! CLIP REMINDER !!";
        }
        return count > 1 ? base + " x" + count : base;
    }

    private static String hint(int kind) {
        if (kind == REMINDER) return "Open chat and click to view it";
        return kind == BOOK || kind == SIGN ? "Open chat and click to open" : "Open chat and click to paste";
    }

    /** the little picture for each kind, drawn at (x,y) with the given pixel scale */
    public static void icon(GuiGraphicsExtractor g, int kind, int x, int y, int scale) {
        if (kind == REMINDER) {
            Icons.BELL.draw(g, x, y, 0xFFFFD36B, 0xFFFFFFFF, scale);
        } else if (kind == SIGN) {
            Icons.SIGN.draw(g, x, y, 0xFFD9B26E, 0xFFFFFFFF, scale);
        } else if (kind == BOOK) {
            Icons.BOOK.draw(g, x, y, 0xFFE7D6A8, 0xFFFFFFFF, scale);
        } else {
            ClipIcon.drawScaled(g, x, y, scale);
        }
    }

    private static int iconW(int kind, int scale) {
        return kind == TEXT ? 12 * scale : Icons.SIZE * scale;
    }

    private static int iconH(int kind, int scale) {
        return kind == TEXT ? 14 * scale : Icons.SIZE * scale;
    }

    /** small one-off confirmation toast in the vanilla look */
    public static void infoToast(GuiGraphicsExtractor g, Font font, int x, int y, int w, String title, String msg) {
        int h = 32;
        rr(g, x, y, w, h, 0xFF000000);
        rr(g, x + 1, y + 1, w - 2, h - 2, 0xFF7A7A7A);
        rr(g, x + 2, y + 2, w - 4, h - 4, 0xFF2A2A2A);
        g.fill(x + 6, y + 4, x + 30, y + 28, 0xFF373737);
        g.fill(x + 7, y + 5, x + 30, y + 28, 0xFFFFFFFF);
        g.fill(x + 7, y + 5, x + 29, y + 27, 0xFF8B8B8B);
        Icons.BOLT.draw(g, x + 12, y + 10, 0xFFFFD36B, 0xFFFFFFFF);
        int tx = x + 34, maxW = w - 34 - 6;
        g.text(font, Component.literal(fit(font, title, maxW)), tx, y + 7, 0xFFFFFF00);
        g.text(font, Component.literal(fit(font, msg, maxW)), tx, y + 18, 0xFFFFFFFF);
    }

    public static void draw(GuiGraphicsExtractor g, Font font, NotifyStyle st, int x, int y, int w,
                            String name, int count, int kind, long t) {
        switch (st) {
            case STANDARD -> standard(g, font, x, y, w, name, count, kind);
            case BOLD -> bolder(g, font, x, y, w, name, count, kind, t);
            case SUPER -> superLoud(g, font, x, y, w, name, count, kind, t);
        }
    }

    /** vanilla-advancement look: dark body, light border, slot-style icon frame, yellow title */
    private static void standard(GuiGraphicsExtractor g, Font font, int x, int y, int w, String name, int count, int kind) {
        int h = 32;
        rr(g, x, y, w, h, 0xFF000000);
        rr(g, x + 1, y + 1, w - 2, h - 2, 0xFF7A7A7A);
        rr(g, x + 2, y + 2, w - 4, h - 4, 0xFF2A2A2A);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, 0xFF3A3A3A);

        g.fill(x + 6, y + 4, x + 30, y + 28, 0xFF373737);
        g.fill(x + 7, y + 5, x + 30, y + 28, 0xFFFFFFFF);
        g.fill(x + 7, y + 5, x + 29, y + 27, 0xFF8B8B8B);
        icon(g, kind, x + 7 + (22 - iconW(kind, 1)) / 2, y + 5 + (22 - iconH(kind, 1)) / 2, 1);

        int tx = x + 34, maxW = w - 34 - 6;
        g.text(font, Component.literal(fit(font, title(NotifyStyle.STANDARD, kind, count), maxW)), tx, y + 7, 0xFFFFFF00);
        g.text(font, Component.literal(message(font, name, kind, maxW)), tx, y + 18, 0xFFFFFFFF);
    }

    private static void bolder(GuiGraphicsExtractor g, Font font, int x, int y, int w, String name, int count, int kind, long t) {
        int h = 46;
        double p = 0.5 + 0.5 * Math.sin(t / 170.0);
        int border = mix(0xFFB8860B, 0xFFFFE066, p);
        rr(g, x, y, w, h, 0xFF000000);
        rr(g, x + 1, y + 1, w - 2, h - 2, border);
        rr(g, x + 3, y + 3, w - 6, h - 6, 0xFF1D1810);
        g.fill(x + 3, y + 3, x + 7, y + h - 3, border);

        g.fill(x + 11, y + 6, x + 41, y + 40, 0xFF000000);
        g.fill(x + 12, y + 7, x + 40, y + 39, mix(0xFF4A3A10, 0xFF7A5E10, p));
        int sc = 2;
        icon(g, kind, x + 12 + (28 - iconW(kind, sc)) / 2, y + 7 + (32 - iconH(kind, sc)) / 2, sc);

        int tx = x + 48, maxW = w - 48 - 8;
        g.text(font, bold(fit(font, title(NotifyStyle.BOLD, kind, count), maxW)), tx, y + 7, mix(0xFFFFC107, 0xFFFFF0A0, p));
        g.text(font, Component.literal(message(font, name, kind, maxW)), tx, y + 19, 0xFFFFFFFF);
        g.text(font, Component.literal(fit(font, hint(kind), maxW)), tx, y + 32, 0xFFB0A070);
    }

    private static void superLoud(GuiGraphicsExtractor g, Font font, int x, int y, int w, String name, int count, int kind, long t) {
        int h = 62;
        boolean flash = (t / 220) % 2 == 0;
        double p = 0.5 + 0.5 * Math.sin(t / 110.0);
        int border = flash ? 0xFFFF2A2A : 0xFFFF9A1F;
        rr(g, x, y, w, h, 0xFF000000);
        rr(g, x + 1, y + 1, w - 2, h - 2, border);
        rr(g, x + 4, y + 4, w - 8, h - 8, mix(0xFF2A0707, 0xFF4A0C0C, p));

        g.fill(x + 10, y + 9, x + 54, y + 53, 0xFF000000);
        g.fill(x + 11, y + 10, x + 53, y + 52, mix(0xFF7A1010, 0xFFB02020, p));
        int sc = 3;
        icon(g, kind, x + 11 + (42 - iconW(kind, sc)) / 2, y + 10 + (42 - iconH(kind, sc)) / 2, sc);

        int tx = x + 62, maxW = w - 62 - 12;
        g.text(font, bold(fit(font, title(NotifyStyle.SUPER, kind, count), maxW)), tx, y + 10, flash ? 0xFFFFFFFF : 0xFFFFD27A);
        g.text(font, bold(message(font, name, kind, maxW)), tx, y + 24, 0xFFFFFFFF);
        g.text(font, Component.literal(fit(font, hint(kind), maxW)), tx, y + 40, 0xFFFFB0B0);
    }

    public static void edgeFlash(GuiGraphicsExtractor g, int sw, int sh, long age, long total) {
        if (age > 4000) return;
        double fade = 1.0 - Math.min(1.0, Math.max(0.0, (age - 2500) / 1500.0));
        double p = 0.5 + 0.5 * Math.sin(age / 120.0);
        int a1 = (int) ((50 + 70 * p) * fade);
        int a2 = (int) ((25 + 35 * p) * fade);
        if (a1 <= 0) return;
        int c1 = (a1 << 24) | 0xFF2020, c2 = (a2 << 24) | 0xFF2020;
        g.fill(0, 0, sw, 10, c1);
        g.fill(0, sh - 10, sw, sh, c1);
        g.fill(0, 10, 10, sh - 10, c1);
        g.fill(sw - 10, 10, sw, sh - 10, c1);
        g.fill(10, 10, sw - 10, 22, c2);
        g.fill(10, sh - 22, sw - 10, sh - 10, c2);
        g.fill(10, 22, 22, sh - 22, c2);
        g.fill(sw - 22, 22, sw - 10, sh - 22, c2);
    }
}
