package com.qualityclip.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Tiny immediate-mode UI helper. Each frame a screen calls begin(), then draws buttons/sliders/toggles through
 * this class, which both paints them and remembers where they are so clicks can be routed back.
 * Uses nothing but fill() and text(), so it works the same on every Minecraft version.
 */
public final class Imm {
    public static final int NORMAL = 0, PRIMARY = 1, DANGER = 2, GHOST = 3;

    private static final class Hit {
        int x, y, w, h;
        Runnable left;
        Runnable right;
    }

    private static final class SliderHit {
        int x, y, w, min, max, step;
        IntConsumer set;
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<SliderHit> sliders = new ArrayList<>();
    private SliderHit dragging;
    private String tip;
    private int tipX, tipY;

    public void begin() {
        hits.clear();
        sliders.clear();
        tip = null;
    }

    public static boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void addHit(int x, int y, int w, int h, Runnable left, Runnable right) {
        Hit hit = new Hit();
        hit.x = x;
        hit.y = y;
        hit.w = w;
        hit.h = h;
        hit.left = left;
        hit.right = right;
        hits.add(hit);
    }

    /** register a clickable area that draws nothing (cards, rows) */
    public boolean area(int mx, int my, int x, int y, int w, int h, Runnable left, Runnable right) {
        addHit(x, y, w, h, left, right);
        return over(mx, my, x, y, w, h);
    }

    private void setTip(String t, int mx, int my) {
        tip = t;
        tipX = mx;
        tipY = my;
    }

    // ---------------------------------------------------------------- buttons

    private static void face(GuiGraphicsExtractor g, int x, int y, int w, int h, int style, boolean hover, boolean enabled) {
        int border = hover && enabled ? 0xFFFFFFFF : Theme.BORDER;
        int face, hi, lo;
        if (!enabled) {
            face = 0xFF242B32;
            hi = 0xFF2C343C;
            lo = 0xFF1C2228;
        } else if (style == PRIMARY) {
            face = hover ? 0xFF38A99D : 0xFF2E8E84;
            hi = 0xFF5CC8BC;
            lo = 0xFF1E6A62;
        } else if (style == DANGER) {
            face = hover ? 0xFFB84A4A : 0xFF9A3C3C;
            hi = 0xFFD06A6A;
            lo = 0xFF6E2A2A;
        } else if (style == GHOST) {
            face = hover ? 0xFF2D3741 : 0xFF222A33;
            hi = 0xFF35414D;
            lo = 0xFF1A2027;
        } else {
            face = hover ? 0xFF4A5663 : 0xFF3A4551;
            hi = 0xFF5D6C7B;
            lo = 0xFF283039;
        }
        Theme.rr(g, x, y, w, h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, hi);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, lo);
    }

    public void btn(GuiGraphicsExtractor g, Font f, int mx, int my, int x, int y, int w, int h, String label,
                    Icons icon, int style, boolean enabled, String tooltip, Runnable act) {
        boolean hover = over(mx, my, x, y, w, h);
        face(g, x, y, w, h, style, hover, enabled);
        int tc = enabled ? Theme.TEXT : Theme.FAINT;
        int iconW = icon != null ? Icons.SIZE + (label != null && !label.isEmpty() ? 4 : 0) : 0;
        String text = label == null ? "" : Theme.fit(f, label, w - 8 - iconW);
        int total = iconW + f.width(text);
        int sx = x + (w - total) / 2;
        if (icon != null) icon.draw(g, sx, y + (h - Icons.SIZE) / 2, tc, enabled ? Theme.GOLD : tc);
        if (!text.isEmpty()) {
            g.text(f, Component.literal(text), sx + iconW, y + (h - 8) / 2, tc);
        }
        if (enabled && act != null) addHit(x, y, w, h, act, null);
        if (hover && tooltip != null) setTip(tooltip, mx, my);
    }

    public void iconBtn(GuiGraphicsExtractor g, Font f, int mx, int my, int x, int y, int size, Icons icon, int style,
                        boolean enabled, boolean selected, String tooltip, Runnable act) {
        boolean hover = over(mx, my, x, y, size, size);
        face(g, x, y, size, size, style, hover, enabled);
        if (selected) {
            g.fill(x, y + size - 2, x + size, y + size, Theme.GOLD);
        }
        int c = !enabled ? Theme.FAINT : (style == DANGER ? 0xFFFFE0E0 : Theme.TEXT);
        icon.draw(g, x + (size - Icons.SIZE) / 2, y + (size - Icons.SIZE) / 2, c, enabled ? Theme.GOLD : c);
        if (enabled && act != null) addHit(x, y, size, size, act, null);
        if (hover && tooltip != null) setTip(tooltip, mx, my);
    }

    /** segmented control: equal-width options; returns nothing, selection reported through set */
    public void seg(GuiGraphicsExtractor g, Font f, int mx, int my, int x, int y, int w, int h, String[] labels,
                    int selected, IntConsumer set) {
        int n = labels.length;
        int cw = w / n;
        Theme.rr(g, x - 1, y - 1, cw * n + 2, h + 2, Theme.BORDER);
        for (int i = 0; i < n; i++) {
            int cx = x + i * cw;
            boolean sel = i == selected;
            boolean hover = over(mx, my, cx, y, cw, h);
            int face = sel ? 0xFF2E8E84 : (hover ? 0xFF38424D : 0xFF2A323B);
            g.fill(cx, y, cx + cw, y + h, face);
            if (sel) g.fill(cx, y + h - 2, cx + cw, y + h, Theme.GOLD);
            else if (i > 0) g.fill(cx, y + 2, cx + 1, y + h - 2, Theme.BORDER);
            String t = Theme.fit(f, labels[i], cw - 6);
            g.text(f, Component.literal(t), cx + (cw - f.width(t)) / 2, y + (h - 8) / 2, sel ? Theme.TEXT : Theme.MUTED);
            final int idx = i;
            addHit(cx, y, cw, h, () -> set.accept(idx), null);
        }
    }

    public void toggle(GuiGraphicsExtractor g, int mx, int my, int x, int y, boolean on, Runnable flip) {
        int w = 24, h = 12;
        boolean hover = over(mx, my, x, y, w, h);
        Theme.rr(g, x - 1, y - 1, w + 2, h + 2, hover ? 0xFFFFFFFF : Theme.BORDER);
        g.fill(x, y, x + w, y + h, on ? 0xFF2E8E84 : 0xFF3A4551);
        int kx = on ? x + w - 11 : x + 1;
        g.fill(kx, y + 1, kx + 10, y + h - 1, 0xFFE9EEF3);
        g.fill(kx, y + h - 2, kx + 10, y + h - 1, 0xFFB7C0CA);
        addHit(x - 2, y - 2, w + 4, h + 4, flip, null);
    }

    /** horizontal slider with a knob; value reported through set while dragging */
    public void slider(GuiGraphicsExtractor g, Font f, int mx, int my, int x, int y, int w, int value, int min,
                       int max, int step, IntConsumer set) {
        int th = 6;
        int ty = y + 5;
        Theme.rr(g, x - 1, ty - 1, w + 2, th + 2, Theme.BORDER);
        g.fill(x, ty, x + w, ty + th, 0xFF11171C);
        double frac = max == min ? 0 : (value - min) / (double) (max - min);
        int kx = x + (int) Math.round(frac * (w - 8));
        g.fill(x, ty, kx + 4, ty + th, 0xFF2E8E84);
        boolean hover = over(mx, my, x - 2, y, w + 4, 16) || (dragging != null && dragging.x == x);
        g.fill(kx - 1, y + 1, kx + 9, y + 15, hover ? 0xFFFFFFFF : Theme.BORDER);
        g.fill(kx, y + 2, kx + 8, y + 14, hover ? 0xFFF4F7FA : 0xFFCBD3DB);
        g.fill(kx, y + 2, kx + 8, y + 3, 0xFFFFFFFF);
        g.fill(kx + 3, y + 5, kx + 5, y + 11, 0xFF6B7885);
        SliderHit s = new SliderHit();
        s.x = x;
        s.y = y;
        s.w = w;
        s.min = min;
        s.max = max;
        s.step = step;
        s.set = set;
        sliders.add(s);
        addHit(x - 2, y, w + 4, 16, null, null);
    }

    private static int sliderValue(SliderHit s, int mx) {
        double frac = (mx - s.x - 4) / (double) Math.max(1, s.w - 8);
        frac = Math.max(0.0, Math.min(1.0, frac));
        double raw = s.min + frac * (s.max - s.min);
        int v = s.min + (int) Math.round((raw - s.min) / s.step) * s.step;
        return Math.max(s.min, Math.min(s.max, v));
    }

    // ---------------------------------------------------------------- input routing

    /** @return true when something was hit */
    public boolean click(int mx, int my, boolean left) {
        for (int i = sliders.size() - 1; i >= 0; i--) {
            SliderHit s = sliders.get(i);
            if (left && over(mx, my, s.x - 2, s.y, s.w + 4, 16)) {
                dragging = s;
                s.set.accept(sliderValue(s, mx));
                return true;
            }
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (over(mx, my, h.x, h.y, h.w, h.h)) {
                Runnable r = left ? h.left : h.right;
                if (r != null) {
                    r.run();
                    return true;
                }
                if (h.left == null && h.right == null) return true; // slider rows etc.
            }
        }
        return false;
    }

    public boolean drag(int mx) {
        if (dragging == null) return false;
        dragging.set.accept(sliderValue(dragging, mx));
        return true;
    }

    public boolean release() {
        boolean was = dragging != null;
        dragging = null;
        return was;
    }

    public void drawTip(GuiGraphicsExtractor g, Font f, int screenW) {
        if (tip != null && dragging == null) {
            com.qualityclip.notify.Tip.draw(g, f, tip, tipX + 10, tipY - 20, screenW);
        }
    }
}
