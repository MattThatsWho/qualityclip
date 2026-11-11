package com.qualityclip.ui;

import com.qualityclip.compat.Compat;
import com.qualityclip.compat.Safe;
import com.qualityclip.notify.Fmt;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A multi-line text editor core with real selection: click-drag, shift+arrows, double-click word, Ctrl+A/C/X/V,
 * typing over a selection, wrapping and optional scrolling. Used for Book & Quill pages
 * (114px / 14 lines / 1023 chars) and for reminder details. Understands the section-sign colour codes, and can
 * apply a colour/format to the selection.
 */
public final class TextArea {
    public static final int SELECTION_COLOR = 0x903B6BFF;

    public String text = "";
    public int cursor;
    /** other end of the selection; equals cursor when nothing is selected */
    public int anchor;
    public int firstLine;

    private int widthPx;
    private final int maxChars;
    private final int maxLines; // 0 = unlimited
    private Fmt.Layout layout;
    private Font layoutFont;
    private long lastClickMs;
    private int lastClickIndex = -1;

    public TextArea(int widthPx, int maxChars, int maxLines) {
        this.widthPx = widthPx;
        this.maxChars = maxChars;
        this.maxLines = maxLines;
    }

    public void setWidth(int w) {
        if (w != widthPx) {
            widthPx = w;
            layout = null;
        }
    }

    public void set(String t) {
        text = t == null ? "" : t;
        cursor = anchor = text.length();
        firstLine = 0;
        layout = null;
    }

    public Fmt.Layout layout(Font font) {
        if (layout == null || layoutFont != font || !layout.text.equals(text)) {
            layout = Fmt.layout(font, text, widthPx);
            layoutFont = font;
        }
        return layout;
    }

    private void clamp() {
        cursor = Math.max(0, Math.min(cursor, text.length()));
        anchor = Math.max(0, Math.min(anchor, text.length()));
    }

    // ------------------------------------------------------------ selection

    public boolean hasSelection() {
        return anchor != cursor;
    }

    public int selStart() {
        return Math.min(anchor, cursor);
    }

    public int selEnd() {
        return Math.max(anchor, cursor);
    }

    public String selectedText() {
        clamp();
        return hasSelection() ? text.substring(selStart(), selEnd()) : "";
    }

    public void selectAll() {
        anchor = 0;
        cursor = text.length();
    }

    private void collapse() {
        anchor = cursor;
    }

    private void move(int newCursor, boolean extend) {
        cursor = Math.max(0, Math.min(newCursor, text.length()));
        if (!extend) anchor = cursor;
    }

    // ------------------------------------------------------------ editing

    private boolean replace(Font font, int s, int e, String repl) {
        String nt = text.substring(0, s) + repl + text.substring(e);
        if (nt.length() > maxChars) return false;
        if (maxLines > 0 && Fmt.layout(font, nt, widthPx).lines.size() > maxLines) return false;
        text = nt;
        cursor = anchor = s + repl.length();
        return true;
    }

    /** inserts at the cursor, replacing any selection. @return false when it would not fit (nothing changes) */
    public boolean insert(Font font, String s) {
        clamp();
        return replace(font, selStart(), selEnd(), s);
    }

    /** paste-style insert: drops control characters, stops at the first thing that does not fit */
    public boolean insertFiltered(Font font, String raw, boolean allowCodes) {
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '\r') continue;
            if (ch == Fmt.SECTION && !allowCodes) {
                i++; // drop the whole colour code, not just the section sign
                continue;
            }
            if (ch != '\n' && (ch < 32 || ch == 127)) continue;
            clean.append(ch);
        }
        clamp();
        if (hasSelection() && !clean.isEmpty()) {
            // paste over the selection, but only if at least the first character fits
            int s = selStart(), e = selEnd();
            String first = clean.substring(0, 1);
            if (!replace(font, s, e, first)) return false;
            clean.deleteCharAt(0);
        } else if (hasSelection()) {
            deleteSelection();
        }
        for (int i = 0; i < clean.length(); i++) {
            if (!replace(font, cursor, cursor, String.valueOf(clean.charAt(i)))) return false;
        }
        return true;
    }

    public void typed(Font font, int cp, boolean allowCodes) {
        if (cp < 32 || cp == 127 || cp > 0xFFFF) return;
        if (cp == Fmt.SECTION && !allowCodes) return;
        insert(font, String.valueOf((char) cp));
    }

    private void deleteSelection() {
        int s = selStart(), e = selEnd();
        text = text.substring(0, s) + text.substring(e);
        cursor = anchor = s;
    }

    public void backspace() {
        clamp();
        if (hasSelection()) {
            deleteSelection();
            return;
        }
        if (cursor <= 0) return;
        int step = (cursor >= 2 && text.charAt(cursor - 2) == Fmt.SECTION) ? 2 : 1;
        text = text.substring(0, cursor - step) + text.substring(cursor);
        cursor -= step;
        anchor = cursor;
    }

    public void delete() {
        clamp();
        if (hasSelection()) {
            deleteSelection();
            return;
        }
        if (cursor >= text.length()) return;
        int step = (text.charAt(cursor) == Fmt.SECTION && cursor + 1 < text.length()) ? 2 : 1;
        text = text.substring(0, cursor) + text.substring(cursor + step);
        anchor = cursor;
    }

    private int stepLeft() {
        return (cursor >= 2 && text.charAt(cursor - 2) == Fmt.SECTION) ? 2 : 1;
    }

    private int stepRight() {
        return (cursor < text.length() && text.charAt(cursor) == Fmt.SECTION && cursor + 1 < text.length()) ? 2 : 1;
    }

    public void left(boolean extend) {
        clamp();
        if (!extend && hasSelection()) {
            move(selStart(), false);
            return;
        }
        if (cursor > 0) move(cursor - stepLeft(), extend);
    }

    public void right(boolean extend) {
        clamp();
        if (!extend && hasSelection()) {
            move(selEnd(), false);
            return;
        }
        if (cursor < text.length()) move(cursor + stepRight(), extend);
    }

    public void vertical(Font font, int dir, boolean extend) {
        clamp();
        Fmt.Layout L = layout(font);
        int line = Fmt.lineOf(L, cursor);
        int x = Fmt.xOf(L, line, cursor);
        int target = line + dir;
        if (target < 0) move(0, extend);
        else if (target >= L.lines.size()) move(text.length(), extend);
        else move(Fmt.indexAt(L, target, x), extend);
    }

    public void home(Font font, boolean extend) {
        clamp();
        Fmt.Layout L = layout(font);
        move(L.lines.get(Fmt.lineOf(L, cursor))[0], extend);
    }

    public void end(Font font, boolean extend) {
        clamp();
        Fmt.Layout L = layout(font);
        move(L.lines.get(Fmt.lineOf(L, cursor))[1], extend);
    }

    /** @return true if the key was a text-editing key and has been handled */
    public boolean key(Font font, int key, int mods, boolean allowCodes) {
        boolean ctrl = Compat.ctrl(mods);
        boolean shift = Compat.shift(mods);
        if (ctrl && key == Compat.K_V) {
            insertFiltered(font, Safe.clipboard(), allowCodes);
            return true;
        }
        if (ctrl && key == Compat.K_A) {
            selectAll();
            return true;
        }
        if (ctrl && key == Compat.K_C) {
            if (hasSelection()) Safe.copy(selectedText());
            return true;
        }
        if (ctrl && key == Compat.K_X) {
            if (hasSelection()) {
                Safe.copy(selectedText());
                deleteSelection();
            }
            return true;
        }
        if (key == Compat.K_BACKSPACE) {
            backspace();
        } else if (key == Compat.K_DELETE) {
            delete();
        } else if (key == Compat.K_ENTER || key == Compat.K_KP_ENTER) {
            insert(font, "\n");
        } else if (key == Compat.K_LEFT) {
            left(shift);
        } else if (key == Compat.K_RIGHT) {
            right(shift);
        } else if (key == Compat.K_UP) {
            vertical(font, -1, shift);
        } else if (key == Compat.K_DOWN) {
            vertical(font, 1, shift);
        } else if (key == Compat.K_HOME) {
            home(font, shift);
        } else if (key == Compat.K_END) {
            end(font, shift);
        } else {
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------ formatting

    private static String hexOf(int rgb) {
        for (int i = 0; i < Fmt.PALETTE.length; i++) if (Fmt.PALETTE[i] == rgb) return String.valueOf(Fmt.HEX.charAt(i));
        return null;
    }

    /** full explicit state as codes, e.g. colour red + bold -> "§c§l" */
    static String codesFor(int color, int flags) {
        StringBuilder sb = new StringBuilder();
        String hex = color >= 0 ? hexOf(color) : null;
        sb.append(Fmt.SECTION).append(hex != null ? hex : "r");
        if ((flags & Fmt.BOLD) != 0) sb.append(Fmt.SECTION).append('l');
        if ((flags & Fmt.ITALIC) != 0) sb.append(Fmt.SECTION).append('o');
        if ((flags & Fmt.UNDER) != 0) sb.append(Fmt.SECTION).append('n');
        if ((flags & Fmt.STRIKE) != 0) sb.append(Fmt.SECTION).append('m');
        if ((flags & Fmt.OBF) != 0) sb.append(Fmt.SECTION).append('k');
        return sb.toString();
    }

    /** formatting state in effect just before index i (i may equal text length) */
    public int[] stateAt(Font font, int i) {
        Fmt.Layout L = layout(font);
        if (i < L.text.length()) return new int[]{L.color[i], L.flags[i]};
        int color = -1, flags = 0;
        for (int k = 0; k < L.text.length(); k++) {
            if (L.text.charAt(k) == Fmt.SECTION && k + 1 < L.text.length()) {
                char c = Character.toLowerCase(L.text.charAt(k + 1));
                int p = Fmt.HEX.indexOf(c);
                if (p >= 0) {
                    color = Fmt.PALETTE[p];
                    flags = 0;
                } else if (c == 'l') flags |= Fmt.BOLD;
                else if (c == 'o') flags |= Fmt.ITALIC;
                else if (c == 'n') flags |= Fmt.UNDER;
                else if (c == 'm') flags |= Fmt.STRIKE;
                else if (c == 'k') flags |= Fmt.OBF;
                else if (c == 'r') {
                    color = -1;
                    flags = 0;
                }
                k++;
            }
        }
        return new int[]{color, flags};
    }

    private static int flagBit(char c) {
        return switch (c) {
            case 'l' -> Fmt.BOLD;
            case 'o' -> Fmt.ITALIC;
            case 'n' -> Fmt.UNDER;
            case 'm' -> Fmt.STRIKE;
            case 'k' -> Fmt.OBF;
            default -> 0;
        };
    }

    /**
     * Applies a colour (hex digit), style (l o n m k) or reset (r). With a selection the whole selection changes
     * and the text after it keeps its old look; with no selection the code is inserted at the cursor.
     * @return false if it would not fit
     */
    public boolean applyFormat(Font font, String code) {
        clamp();
        char c = Character.toLowerCase(code.charAt(0));
        if (!hasSelection()) return insert(font, String.valueOf(Fmt.SECTION) + c);
        int s = selStart(), e = selEnd();
        int[] before = stateAt(font, s);
        int[] after = stateAt(font, e);
        int color = before[0], flags = before[1];
        int p = Fmt.HEX.indexOf(c);
        if (p >= 0) {
            color = Fmt.PALETTE[p];
        } else if (c == 'r') {
            color = -1;
            flags = 0;
        } else {
            int bit = flagBit(c);
            if (bit == 0) return false;
            flags = (flags & bit) != 0 ? flags & ~bit : flags | bit; // toggle
        }
        String inner = Fmt.strip(text.substring(s, e));
        String open = codesFor(color, flags);
        String close = codesFor(after[0], after[1]);
        String repl = open + inner + close;
        String nt = text.substring(0, s) + repl + text.substring(e);
        if (nt.length() > maxChars) return false;
        if (maxLines > 0 && Fmt.layout(font, nt, widthPx).lines.size() > maxLines) return false;
        text = nt;
        anchor = s + open.length();
        cursor = anchor + inner.length();
        return true;
    }

    // ------------------------------------------------------------ mouse + drawing

    private int indexAtLocal(Font font, int localX, int localY) {
        Fmt.Layout L = layout(font);
        int line = firstLine + Math.max(0, localY / Fmt.LINE_H);
        line = Math.max(0, Math.min(L.lines.size() - 1, line));
        return Fmt.indexAt(L, line, localX);
    }

    /** mouse button went down at local text coordinates */
    public void press(Font font, int localX, int localY, boolean shift, int visibleLines) {
        int idx = indexAtLocal(font, localX, localY);
        long now = System.currentTimeMillis();
        if (idx == lastClickIndex && now - lastClickMs < 350 && !shift) {
            // double-click: select the word
            int s = idx, e = idx;
            while (s > 0 && !Character.isWhitespace(text.charAt(s - 1))) s--;
            while (e < text.length() && !Character.isWhitespace(text.charAt(e))) e++;
            anchor = s;
            cursor = e;
            lastClickIndex = -1;
        } else {
            cursor = idx;
            if (!shift) anchor = idx;
            lastClickIndex = idx;
            lastClickMs = now;
        }
        ensureVisible(font, visibleLines);
    }

    /** mouse dragged while held */
    public void drag(Font font, int localX, int localY, int visibleLines) {
        int idx = indexAtLocal(font, localX, localY);
        cursor = idx;
        ensureVisible(font, visibleLines);
    }

    public void scroll(Font font, int deltaLines, int visibleLines) {
        Fmt.Layout L = layout(font);
        firstLine = Math.max(0, Math.min(Math.max(0, L.lines.size() - visibleLines), firstLine + deltaLines));
    }

    public void ensureVisible(Font font, int visibleLines) {
        Fmt.Layout L = layout(font);
        clamp();
        int line = Fmt.lineOf(L, cursor);
        if (line < firstLine) firstLine = line;
        if (line >= firstLine + visibleLines) firstLine = line - visibleLines + 1;
        firstLine = Math.max(0, Math.min(firstLine, Math.max(0, L.lines.size() - visibleLines)));
    }

    public void draw(GuiGraphicsExtractor g, Font font, int x, int y, int visibleLines, boolean focused, int color) {
        Fmt.Layout L = layout(font);
        ensureVisible(font, visibleLines);
        clamp();
        if (hasSelection()) {
            int s = selStart(), e = selEnd();
            for (int i = 0; i < visibleLines; i++) {
                int li = firstLine + i;
                if (li >= L.lines.size()) break;
                int[] ln = L.lines.get(li);
                int a = Math.max(s, ln[0]), b = Math.min(e, ln[1]);
                boolean newlineSel = e > ln[1] && s <= ln[1] && ln[1] < text.length() && text.charAt(ln[1]) == '\n';
                if (b > a || newlineSel) {
                    int x1 = x + Fmt.xOf(L, li, a);
                    int x2 = x + Fmt.xOf(L, li, Math.max(a, b)) + (newlineSel ? 3 : 0);
                    if (x2 <= x1) x2 = x1 + 1;
                    g.fill(x1, y + i * Fmt.LINE_H - 1, x2, y + i * Fmt.LINE_H + 8, SELECTION_COLOR);
                }
            }
        }
        for (int i = 0; i < visibleLines; i++) {
            int li = firstLine + i;
            if (li >= L.lines.size()) break;
            Fmt.drawLine(g, font, L, li, x, y + i * Fmt.LINE_H, color);
        }
        if (focused && !hasSelection() && (System.currentTimeMillis() / 500) % 2 == 0) {
            int line = Fmt.lineOf(L, cursor);
            if (line >= firstLine && line < firstLine + visibleLines) {
                int cx = x + Fmt.xOf(L, line, cursor);
                int cy = y + (line - firstLine) * Fmt.LINE_H;
                g.fill(cx, cy - 1, cx + 1, cy + 9, color);
            }
        }
    }

    public int lineCount(Font font) {
        return layout(font).lines.size();
    }
}
