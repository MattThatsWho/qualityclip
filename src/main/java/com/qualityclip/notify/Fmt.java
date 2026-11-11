package com.qualityclip.notify;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Text layout for notes pages. Mirrors Book & Quill limits so pages paste 1:1:
 * 114 px wide, 14 lines, 1023 characters. Understands the section-sign colour/format codes that
 * Scribble adds. Width/draw use only Component styling so it never depends on how the game treats raw codes.
 */
public final class Fmt {
    public static final int TEXT_W = 114;
    public static final int MAX_LINES = 14;
    public static final int MAX_CHARS = 1023;
    public static final int LINE_H = 9;

    public static final int BOLD = 1, ITALIC = 2, UNDER = 4, STRIKE = 8, OBF = 16;
    public static final char SECTION = '\u00A7';

    public static final int[] PALETTE = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    public static final String[] COLOR_NAMES = {
            "Black", "Dark Blue", "Dark Green", "Dark Aqua", "Dark Red", "Dark Purple", "Gold", "Gray",
            "Dark Gray", "Blue", "Green", "Aqua", "Red", "Light Purple", "Yellow", "White"};
    public static final String HEX = "0123456789abcdef";

    private static final Map<Integer, Integer> WIDTHS = new HashMap<>();

    private Fmt() {}

    public static final class Layout {
        public String text = "";
        public int[] width = new int[0];
        public int[] color = new int[0];
        public int[] flags = new int[0];
        public boolean[] code = new boolean[0];
        /** each entry: {startInclusive, endExclusive} */
        public final List<int[]> lines = new ArrayList<>();
    }

    public static int charWidth(Font font, char ch, boolean bold) {
        int key = ch | (bold ? 0x10000 : 0);
        Integer cached = WIDTHS.get(key);
        if (cached != null) return cached;
        int w;
        if (bold) {
            w = font.width(Component.literal(String.valueOf(ch)).withStyle(st -> st.withBold(Boolean.TRUE)));
        } else {
            w = font.width(String.valueOf(ch));
        }
        WIDTHS.put(key, w);
        return w;
    }

    public static String strip(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == SECTION && i + 1 < s.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static Layout layout(Font font, String text) {
        return layout(font, text, TEXT_W);
    }

    public static Layout layout(Font font, String text, int maxW) {
        Layout L = new Layout();
        int n = text.length();
        L.text = text;
        L.width = new int[n];
        L.color = new int[n];
        L.flags = new int[n];
        L.code = new boolean[n];

        int color = -1, flags = 0;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            L.color[i] = color;
            L.flags[i] = flags;
            if (c == SECTION && i + 1 < n) {
                L.code[i] = true;
                L.code[i + 1] = true;
                char k = Character.toLowerCase(text.charAt(i + 1));
                int p = HEX.indexOf(k);
                if (p >= 0) {
                    color = PALETTE[p];
                    flags = 0;
                } else {
                    switch (k) {
                        case 'l' -> flags |= BOLD;
                        case 'o' -> flags |= ITALIC;
                        case 'n' -> flags |= UNDER;
                        case 'm' -> flags |= STRIKE;
                        case 'k' -> flags |= OBF;
                        case 'r' -> {
                            color = -1;
                            flags = 0;
                        }
                        default -> {
                        }
                    }
                }
                L.color[i + 1] = color;
                L.flags[i + 1] = flags;
                i++;
                continue;
            }
            if (c != '\n') {
                L.width[i] = charWidth(font, c, (flags & BOLD) != 0);
            }
        }

        int start = 0, w = 0, lastBreak = -1, i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\n') {
                L.lines.add(new int[]{start, i});
                start = i + 1;
                w = 0;
                lastBreak = -1;
                i++;
                continue;
            }
            int cw = L.width[i];
            if (cw > 0 && w + cw > maxW && i > start) {
                int brk = lastBreak > start ? lastBreak : i;
                L.lines.add(new int[]{start, brk});
                start = brk;
                w = 0;
                lastBreak = -1;
                for (int j = start; j < i; j++) {
                    w += L.width[j];
                    if (text.charAt(j) == ' ') lastBreak = j + 1;
                }
                continue;
            }
            w += cw;
            if (c == ' ') lastBreak = i + 1;
            i++;
        }
        L.lines.add(new int[]{start, n});
        return L;
    }

    /** line the cursor sits on (later line wins at a soft-wrap boundary) */
    public static int lineOf(Layout L, int cursor) {
        int found = 0;
        for (int i = 0; i < L.lines.size(); i++) {
            int[] ln = L.lines.get(i);
            if (cursor >= ln[0] && cursor <= ln[1]) found = i;
        }
        return found;
    }

    public static int xOf(Layout L, int line, int cursor) {
        int s = L.lines.get(line)[0];
        int x = 0;
        for (int j = s; j < cursor && j < L.width.length; j++) x += L.width[j];
        return x;
    }

    /** nearest cursor index on a line for a pixel x */
    public static int indexAt(Layout L, int line, int px) {
        int[] ln = L.lines.get(line);
        int acc = 0;
        int best = ln[1];
        for (int j = ln[0]; j < ln[1]; j++) {
            int w = L.width[j];
            if (px < acc + (w + 1) / 2) {
                best = j;
                break;
            }
            acc += w;
        }
        if (best > ln[0] && best < L.text.length() && L.code[best] && L.text.charAt(best - 1) == SECTION) best--;
        return best;
    }

    private static Style styled(Style s, int col, int fl) {
        if (col >= 0) s = s.withColor(col);
        if ((fl & BOLD) != 0) s = s.withBold(Boolean.TRUE);
        if ((fl & ITALIC) != 0) s = s.withItalic(Boolean.TRUE);
        if ((fl & UNDER) != 0) s = s.withUnderlined(Boolean.TRUE);
        if ((fl & STRIKE) != 0) s = s.withStrikethrough(Boolean.TRUE);
        return s;
    }

    public static void drawLine(GuiGraphicsExtractor g, Font font, Layout L, int lineIdx, int x, int y, int defaultColor) {
        int[] ln = L.lines.get(lineIdx);
        int i = ln[0], end = ln[1], cx = x;
        while (i < end) {
            if (L.code[i] || L.text.charAt(i) == '\n') {
                i++;
                continue;
            }
            final int col = L.color[i], fl = L.flags[i];
            StringBuilder sb = new StringBuilder();
            int j = i;
            while (j < end && (L.code[j] || (L.color[j] == col && L.flags[j] == fl))) {
                if (!L.code[j]) sb.append(L.text.charAt(j));
                j++;
            }
            Component run = Component.literal(sb.toString()).withStyle(st -> styled(st, col, fl));
            g.text(font, run, cx, y, defaultColor, false);
            cx += font.width(run);
            i = j;
        }
    }
}
