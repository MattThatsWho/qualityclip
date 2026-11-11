package com.qualityclip.screen;

import com.qualityclip.QualityClipMod;
import com.qualityclip.compat.BookBridge;
import com.qualityclip.compat.Compat;
import com.qualityclip.compat.Safe;
import com.qualityclip.data.Clip;
import com.qualityclip.data.ClipStore;
import com.qualityclip.data.Settings;
import com.qualityclip.data.Share;
import com.qualityclip.notify.EditorHook;
import com.qualityclip.notify.Fmt;
import com.qualityclip.ui.Icons;
import com.qualityclip.ui.Imm;
import com.qualityclip.ui.TextArea;
import com.qualityclip.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notes editor. Pages follow Book & Quill rules exactly (114px wide, 14 lines, 1023 characters, 100 pages),
 * so text wraps and overflows the same way and pastes 1:1. It looks like a book but clearly is not the vanilla one:
 * teal cover, gold trim, bookmark ribbon. With Scribble installed a formatting toolbar appears.
 */
public class NotebookScreen extends Screen {
    private static final int PAPER_W = 130;
    private static final int PAPER_H = 146;
    private static final int TXT_X = 8;
    private static final int TXT_Y = 8;
    private static final int SPINE = 8;
    private static final int MAX_PAGES = BookBridge.MAX_PAGES;

    private final Clip clip;
    private final Screen back;
    private final Screen host;
    private final boolean isNew;
    private final Imm imm = new Imm();
    private final boolean scribble = Safe.scribble();
    private final TextArea ta = new TextArea(Fmt.TEXT_W, Fmt.MAX_CHARS, Fmt.MAX_LINES);
    private final Map<Integer, Fmt.Layout> cache = new HashMap<>();

    private boolean dead;
    private int page;
    private int active;
    private boolean editing;
    private boolean confirmDelete;
    private boolean confirmBook;
    private boolean dragging;
    private int dragPx, dragPy;
    private String status = "";
    private long statusUntil;
    private boolean statusBad;
    private EditBox nameBox;
    private EditBox proxy;

    public NotebookScreen(Clip clip, Screen back, Screen host, boolean isNew) {
        super(Component.literal("Notes"));
        this.clip = clip;
        this.back = back;
        this.host = host;
        this.isNew = isNew;
        ta.set(pages().get(0));
    }

    // ------------------------------------------------------------ helpers

    private List<String> pages() {
        return clip.pages();
    }

    private boolean two() {
        return Settings.get().twoPageView && this.width >= (scribble ? 392 : 340);
    }

    private int bookW() {
        return two() ? PAPER_W * 2 + SPINE : PAPER_W;
    }

    private int bx() {
        return (this.width - bookW()) / 2;
    }

    private int y0() {
        return Math.max(2, (this.height - 176) / 2);
    }

    private int paperTop() {
        return y0() + 24;
    }

    private int paperX(int slot) {
        return bx() + slot * (PAPER_W + SPINE);
    }

    private int leftPage() {
        return two() ? (page / 2) * 2 : page;
    }

    private Fmt.Layout layoutOf(int idx) {
        String t = pages().get(idx);
        Fmt.Layout L = cache.get(idx);
        if (L == null || !L.text.equals(t)) {
            L = Fmt.layout(this.font, t);
            cache.put(idx, L);
        }
        return L;
    }

    private void say(String msg, boolean bad) {
        status = msg;
        statusBad = bad;
        statusUntil = System.currentTimeMillis() + 3500;
    }

    private void fail(Throwable t) {
        if (dead) return;
        dead = true;
        QualityClipMod.LOGGER.error("QualityClip notebook closed after an error", t);
        try {
            sync();
            ClipStore.save();
            Compat.setScreen(back);
        } catch (Throwable ignored) {
        }
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    /** write the editor text back into the clip */
    private void sync() {
        if (active >= 0 && active < pages().size() && !ta.text.equals(pages().get(active))) {
            pages().set(active, ta.text);
            ClipStore.save();
        }
    }

    private void setEditing(boolean on) {
        editing = on;
        try {
            if (proxy != null) proxy.setFocused(on);
        } catch (Throwable ignored) {
        }
    }

    /** read by the game (26.3+) to know a custom text box is taking keystrokes */
    public boolean isInputCaptured() {
        return editing;
    }

    private void activate(int idx) {
        sync();
        active = Math.max(0, Math.min(pages().size() - 1, idx));
        ta.set(pages().get(active));
        confirmDelete = false;
    }

    // ------------------------------------------------------------ setup

    @Override
    protected void init() {
        try {
            int mid = bx() + bookW() / 2;
            nameBox = addRenderableWidget(new EditBox(this.font, mid - 70, y0(), 140, 16, Component.literal("Notes name")));
            nameBox.setMaxLength(32);
            nameBox.setValue(clip.name);
            nameBox.setResponder(v -> {
                clip.name = v;
                ClipStore.save();
            });
            proxy = addRenderableWidget(new EditBox(this.font, -100, -100, 10, 10, Component.literal("")));
            if (editing) setEditing(true);
        } catch (Throwable t) {
            fail(t);
        }
    }

    // ------------------------------------------------------------ actions

    private void go(int dir) {
        int step = two() ? 2 : 1;
        int target = Math.max(0, Math.min(pages().size() - 1, leftPage() + dir * step));
        page = target;
        activate(two() ? leftPage() : page);
    }

    private void nextPage() {
        int step = two() ? 2 : 1;
        int target = leftPage() + step;
        if (target >= pages().size()) {
            if (pages().size() >= MAX_PAGES) {
                say("A book can only have " + MAX_PAGES + " pages.", true);
                return;
            }
            addPageAfter(pages().size() - 1);
            return;
        }
        go(1);
    }

    private void addPageAfter(int idx) {
        if (pages().size() >= MAX_PAGES) {
            say("A book can only have " + MAX_PAGES + " pages.", true);
            return;
        }
        sync();
        int at = Math.min(idx + 1, pages().size());
        pages().add(at, "");
        cache.clear();
        page = at;
        active = at;
        ta.set("");
        confirmDelete = false;
        setEditing(true);
        ClipStore.save();
        say("Added page " + (at + 1) + ".", false);
    }

    private void deletePage() {
        if (!confirmDelete) {
            confirmDelete = true;
            return;
        }
        confirmDelete = false;
        if (pages().size() <= 1) {
            pages().set(0, "");
            say("Cleared the only page.", false);
        } else {
            pages().remove(active);
            say("Deleted page " + (active + 1) + ".", false);
        }
        cache.clear();
        active = Math.min(active, pages().size() - 1);
        page = active;
        ta.set(pages().get(active));
        ClipStore.save();
    }

    private String copyText(int from, int to) {
        sync();
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to && i < pages().size(); i++) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(pages().get(i));
        }
        String s = sb.toString();
        return scribble ? s : Fmt.strip(s);
    }

    private void exportThis() {
        sync();
        java.nio.file.Path f = Share.export(java.util.List.of(clip), clip.name);
        if (f == null) {
            say("Couldn't write the export file.", true);
            return;
        }
        Safe.copy(f.toString());
        say("Exported to " + f.getFileName() + " (path copied)", false);
    }

    private void copy(String what, String text) {
        say(Safe.copy(text) ? "Copied " + what + "!" : "Couldn't copy on this version.", false);
    }

    private void pasteIntoBook() {
        if (!confirmBook) {
            confirmBook = true;
            say("Warning: this REPLACES everything in your book. Click again to confirm.", true);
            statusUntil = System.currentTimeMillis() + 7000;
            return;
        }
        confirmBook = false;
        sync();
        boolean[] ok = new boolean[1];
        String msg = BookBridge.paste(host, new ArrayList<>(pages()), ok);
        if (ok[0]) {
            EditorHook.say(msg);
            commit();
            Compat.setScreen(host);
        } else {
            say(msg, true);
        }
    }

    private void openSchedule() {
        commit();
        if (back instanceof ClipboardScreen cs) {
            cs.openScheduleFor(clip);
            Compat.setScreen(cs);
        }
    }

    private void commit() {
        sync();
        if (isNew && clip.isBlank() && Clip.DEFAULT_NAME.equals(clip.name) && clip.schedule == null) {
            ClipStore.remove(clip);
        } else {
            if (clip.name == null || clip.name.isBlank()) clip.name = "Untitled";
            ClipStore.save();
        }
    }

    @Override
    public void onClose() {
        setEditing(false);
        commit();
        Compat.setScreen(back);
    }

    private void insertCode(String code) {
        if (!editing) {
            say("Click on a page first.", true);
            return;
        }
        if (!ta.applyFormat(this.font, code)) say("The page is full.", true);
        sync();
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean keyPressed(KeyEvent event) {
        try {
            if (editing && !dead) {
                int k = event.key(), m = event.modifiers();
                if (k == Compat.K_ESC) return super.keyPressed(event);
                if (Compat.ctrl(m) && k == Compat.K_C && !ta.hasSelection()) {
                    copy("page " + (active + 1), copyText(active, active));
                    return true;
                }
                if (k == Compat.K_PGUP) {
                    go(-1);
                    return true;
                }
                if (k == Compat.K_PGDN) {
                    nextPage();
                    return true;
                }
                boolean wasFull = false;
                String before = ta.text;
                if (ta.key(this.font, k, m, scribble)) {
                    if (ta.text.equals(before) && (k == Compat.K_ENTER || k == Compat.K_KP_ENTER)) wasFull = true;
                    if (wasFull) say("The page is full.", true);
                    sync();
                    return true;
                }
            }
        } catch (Throwable t) {
            fail(t);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        try {
            if (editing && !dead) {
                String before = ta.text;
                ta.typed(this.font, event.codepoint(), false);
                if (ta.text.equals(before) && event.codepoint() >= 32) say("The page is full.", true);
                sync();
                return true;
            }
        } catch (Throwable t) {
            fail(t);
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        try {
            int mx = (int) event.x(), my = (int) event.y();
            if (Compat.isLeft(event.button())) {
                if (imm.click(mx, my, true)) return true;
                int slots = two() ? 2 : 1;
                for (int slot = 0; slot < slots; slot++) {
                    int px = paperX(slot), py = paperTop();
                    if (Imm.over(mx, my, px, py, PAPER_W, PAPER_H - 14)) {
                        int idx = leftPage() + slot;
                        if (idx >= pages().size()) {
                            addPageAfter(pages().size() - 1);
                            return true;
                        }
                        if (idx != active) activate(idx);
                        setEditing(true);
                        dragging = true;
                        dragPx = px;
                        dragPy = py;
                        ta.press(this.font, mx - (px + TXT_X), my - (py + TXT_Y), false, Fmt.MAX_LINES);
                        return true;
                    }
                }
            }
            boolean handled = super.mouseClicked(event, doubleClick);
            if (handled) setEditing(false);
            return handled;
        } catch (Throwable t) {
            fail(t);
            return true;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        try {
            if (dragging && editing) {
                ta.drag(this.font, (int) event.x() - (dragPx + TXT_X), (int) event.y() - (dragPy + TXT_Y), Fmt.MAX_LINES);
                return true;
            }
        } catch (Throwable t) {
            fail(t);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (dead) return;
        try {
            imm.begin();
            g.fill(0, 0, this.width, this.height, 0xE00A0E12);
            drawBook(g, mouseX, mouseY);
            drawColumns(g, mouseX, mouseY);
            super.extractRenderState(g, mouseX, mouseY, delta);
            drawStatus(g);
            imm.drawTip(g, this.font, this.width);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void drawBook(GuiGraphicsExtractor g, int mx, int my) {
        int bx = bx(), pt = paperTop(), bw = bookW();
        // teal cover with gold trim
        g.fill(bx - 7, pt - 7, bx + bw + 7, pt + PAPER_H + 7, 0xFF0B2229);
        g.fill(bx - 6, pt - 6, bx + bw + 6, pt + PAPER_H + 6, 0xFFC8A24A);
        g.fill(bx - 5, pt - 5, bx + bw + 5, pt + PAPER_H + 5, 0xFF1C4B5A);

        int slots = two() ? 2 : 1;
        for (int slot = 0; slot < slots; slot++) {
            int px = paperX(slot);
            int idx = leftPage() + slot;
            boolean exists = idx < pages().size();
            g.fill(px - 1, pt - 1, px + PAPER_W + 1, pt + PAPER_H + 1, 0xFF2B1B0C);
            g.fill(px, pt, px + PAPER_W, pt + PAPER_H, exists ? 0xFFF2E6C8 : 0xFF9C9275);
            if (two()) {
                int sx = slot == 0 ? px + PAPER_W - 8 : px;
                g.fill(sx, pt, sx + 8, pt + PAPER_H, 0x14000000);
            }
            if (!exists) {
                String s = "+ Add page";
                g.text(this.font, Component.literal(s), px + (PAPER_W - this.font.width(s)) / 2, pt + PAPER_H / 2 - 4, 0xFF4A4330, false);
                continue;
            }
            if (editing && idx == active) {
                g.fill(px, pt, px + PAPER_W, pt + 1, 0xFFC8A24A);
                g.fill(px, pt + PAPER_H - 1, px + PAPER_W, pt + PAPER_H, 0xFFC8A24A);
                g.fill(px, pt, px + 1, pt + PAPER_H, 0xFFC8A24A);
                g.fill(px + PAPER_W - 1, pt, px + PAPER_W, pt + PAPER_H, 0xFFC8A24A);
            }
            if (idx == active) {
                ta.draw(g, this.font, px + TXT_X, pt + TXT_Y, Fmt.MAX_LINES, editing, 0xFF000000);
            } else {
                Fmt.Layout L = layoutOf(idx);
                for (int i = 0; i < L.lines.size() && i < Fmt.MAX_LINES; i++) {
                    Fmt.drawLine(g, this.font, L, i, px + TXT_X, pt + TXT_Y + i * Fmt.LINE_H, 0xFF000000);
                }
            }
            String num = "- " + (idx + 1) + " -";
            g.text(this.font, Component.literal(num), px + (PAPER_W - this.font.width(num)) / 2, pt + PAPER_H - 11, 0xFF7A6A44, false);
        }
        if (two()) {
            int sx = paperX(0) + PAPER_W;
            g.fill(sx, pt - 5, sx + SPINE, pt + PAPER_H + 5, 0xFF123743);
            g.fill(sx + SPINE / 2, pt - 5, sx + SPINE / 2 + 1, pt + PAPER_H + 5, 0xFFC8A24A);
        }
        // bookmark ribbon
        int rx = bx + bw - 16;
        g.fill(rx, pt - 6, rx + 8, pt + 14, 0xFFB3262E);
        g.fill(rx, pt + 14, rx + 3, pt + 17, 0xFFB3262E);
        g.fill(rx + 5, pt + 14, rx + 8, pt + 17, 0xFFB3262E);

        // vanilla-style page arrows at the bottom corners of the book
        int total = pages().size();
        int firstShown = leftPage();
        int lastShown = two() ? Math.min(firstShown + 1, total - 1) : firstShown;
        int ay = pt + PAPER_H - 13;
        int lx = paperX(0) + 5;
        int rxA = paperX(two() ? 1 : 0) + PAPER_W - 5 - 14;
        boolean canBack = firstShown > 0;
        pageArrow(g, mx, my, lx, ay, Icons.LEFT, canBack, "Previous page", this::goBack);
        boolean atEnd = lastShown >= total - 1;
        pageArrow(g, mx, my, rxA, ay, atEnd ? Icons.PLUS : Icons.RIGHT, !atEnd || total < MAX_PAGES,
                atEnd ? "New page" : "Next page", this::nextPage);
    }

    private void goBack() {
        go(-1);
    }

    private void pageArrow(GuiGraphicsExtractor g, int mx, int my, int x, int y, Icons icon, boolean enabled, String tip, Runnable act) {
        boolean hover = enabled && Imm.over(mx, my, x, y, 14, 12);
        int c = !enabled ? 0x00000000 : hover ? 0xFFB06A1E : 0xFF5A3A1A;
        if (enabled) {
            g.fill(x, y, x + 14, y + 12, hover ? 0x30B06A1E : 0x00000000);
            icon.draw(g, x + 2, y + 1, c, c);
            imm.area(mx, my, x, y, 14, 12, act, null);
        }
        if (hover) com.qualityclip.notify.Tip.draw(g, this.font, tip, mx + 8, my - 18, this.width);
    }

    private void drawColumns(GuiGraphicsExtractor g, int mx, int my) {
        int y0 = y0(), pt = paperTop(), mid = bx() + bookW() / 2;
        // top row: back + schedule beside the name box
        imm.iconBtn(g, this.font, mx, my, mid - 70 - 24, y0 - 2, 20, Icons.BACK, Imm.NORMAL, true, false, "Back (saves automatically)", this::onClose);
        imm.iconBtn(g, this.font, mx, my, mid + 70 + 4, y0 - 2, 20, Icons.BELL, clip.schedule != null ? Imm.PRIMARY : Imm.NORMAL,
                true, false, "Click to set schedule", this::openSchedule);

        // right-hand icon grid (2 columns)
        int rx = bx() + bookW() + 12;
        if (rx + 44 > this.width) rx = this.width - 46;
        int ry = pt;
        int n = 0;
        n = icon(g, mx, my, rx, ry, n, Icons.PLUS, Imm.NORMAL, false, "Add a page after this one", () -> addPageAfter(active));
        n = icon(g, mx, my, rx, ry, n, Icons.TRASH, confirmDelete ? Imm.DANGER : Imm.NORMAL, confirmDelete,
                confirmDelete ? "Click again to delete page " + (active + 1) : "Delete this page",
                this::deletePage);
        n = icon(g, mx, my, rx, ry, n, Icons.COPY, Imm.NORMAL, false, "Copy this page", () -> copy("page " + (active + 1), copyText(active, active)));
        n = icon(g, mx, my, rx, ry, n, Icons.BOOK, Imm.NORMAL, false, "Copy the whole notebook", () -> copy("all " + pages().size() + " pages", copyText(0, pages().size() - 1)));
        if (two()) {
            n = icon(g, mx, my, rx, ry, n, Icons.SPREAD, Imm.NORMAL, false, "Copy both visible pages", () -> {
                int l = leftPage();
                copy("both pages", copyText(l, Math.min(l + 1, pages().size() - 1)));
            });
        }
        n = icon(g, mx, my, rx, ry, n, Icons.SPREAD, Imm.NORMAL, Settings.get().twoPageView,
                !Settings.get().twoPageView ? "Two-page view: off" : two() ? "Two-page view: on" : "Two-page view: on (window too narrow)", () -> {
                    Settings.get().twoPageView = !Settings.get().twoPageView;
                    Settings.save();
                    page = Math.min(page, pages().size() - 1);
                    activate(two() ? leftPage() : page);
                    rebuild();
                });
        n = icon(g, mx, my, rx, ry, n, Icons.EXPORT, Imm.NORMAL, false, "Save this notebook as a .json file", this::exportThis);
        if (com.qualityclip.notify.EditorHook.kind(host) == com.qualityclip.notify.EditorHook.BOOK) {
            icon(g, mx, my, rx, ry, n, Icons.PASTE_IN, confirmBook ? Imm.DANGER : Imm.PRIMARY, confirmBook,
                    confirmBook ? "Click again: REPLACES everything in your book" : "Paste into your open book (replaces it)", this::pasteIntoBook);
        }

        if (scribble) drawToolbar(g, mx, my);
    }

    private int icon(GuiGraphicsExtractor g, int mx, int my, int rx, int ry, int n, Icons ic, int style, boolean selected,
                     String tip, Runnable act) {
        int x = rx + (n % 2) * 24, y = ry + (n / 2) * 24;
        imm.iconBtn(g, this.font, mx, my, x, y, 20, ic, style, true, selected, tip, act);
        return n + 1;
    }

    // ---- Scribble-style formatting toolbar (left side)

    private int toolX() {
        return Math.max(3, bx() - 7 - 56);
    }

    private void drawToolbar(GuiGraphicsExtractor g, int mx, int my) {
        int tx = toolX(), ty = paperTop();
        // current formatting at the cursor
        Fmt.Layout L = layoutOf(active);
        int ci = Math.min(ta.selStart(), L.text.length()) - 1;
        int curColor = ci >= 0 && ci < L.color.length ? L.color[ci] : -1;
        int curFlags = ci >= 0 && ci < L.flags.length ? L.flags[ci] : 0;
        if (ci >= 0 && ci < L.code.length && ci - 1 >= 0 && L.code[ci]) {
            curColor = L.color[ci];
            curFlags = L.flags[ci];
        }

        Theme.panel(g, tx - 3, ty - 3, 54, 108);
        for (int i = 0; i < 16; i++) {
            int sx = tx + (i % 4) * 12, sy = ty + (i / 4) * 12;
            boolean hover = Imm.over(mx, my, sx, sy, 11, 11);
            boolean sel = curColor == Fmt.PALETTE[i];
            g.fill(sx - 1, sy - 1, sx + 11, sy + 11, sel ? Theme.GOLD : hover ? 0xFFFFFFFF : Theme.BORDER);
            g.fill(sx, sy, sx + 10, sy + 10, 0xFF000000 | Fmt.PALETTE[i]);
            final String code = String.valueOf(Fmt.HEX.charAt(i));
            imm.area(mx, my, sx, sy, 11, 11, () -> insertCode(code), null);
            if (hover) com.qualityclip.notify.Tip.draw(g, this.font, Fmt.COLOR_NAMES[i], mx + 10, my - 18, this.width);
        }
        String[] labels = {"B", "I", "U", "S", "?", "R"};
        String[] codes = {"l", "o", "n", "m", "k", "r"};
        String[] names = {"Bold", "Italic", "Underline", "Strikethrough", "Obfuscated (magic)", "Reset formatting"};
        int[] bits = {Fmt.BOLD, Fmt.ITALIC, Fmt.UNDER, Fmt.STRIKE, Fmt.OBF, 0};
        for (int i = 0; i < 6; i++) {
            int sx = tx + (i % 3) * 16, sy = ty + 52 + (i / 3) * 17;
            boolean hover = Imm.over(mx, my, sx, sy, 15, 15);
            boolean on = bits[i] != 0 && (curFlags & bits[i]) != 0;
            g.fill(sx, sy, sx + 15, sy + 15, hover ? 0xFFFFFFFF : on ? Theme.GOLD : Theme.BORDER);
            g.fill(sx + 1, sy + 1, sx + 14, sy + 14, on ? 0xFF2E8E84 : hover ? 0xFF4A5663 : 0xFF3A4551);
            g.fill(sx + 1, sy + 1, sx + 14, sy + 2, 0xFF5D6C7B);
            Component c = Component.literal(labels[i]);
            switch (i) {
                case 0 -> c = Component.literal("B").withStyle(st -> st.withBold(Boolean.TRUE));
                case 1 -> c = Component.literal("I").withStyle(st -> st.withItalic(Boolean.TRUE));
                case 2 -> c = Component.literal("U").withStyle(st -> st.withUnderlined(Boolean.TRUE));
                case 3 -> c = Component.literal("S").withStyle(st -> st.withStrikethrough(Boolean.TRUE));
                default -> {
                }
            }
            g.text(this.font, c, sx + 8 - this.font.width(c) / 2, sy + 4, Theme.TEXT);
            final String code = codes[i];
            imm.area(mx, my, sx, sy, 15, 15, () -> insertCode(code), null);
            if (hover) com.qualityclip.notify.Tip.draw(g, this.font, names[i], mx + 10, my - 18, this.width);
        }
        g.text(this.font, Component.literal(Theme.fit(this.font, "Scribble", 50)), tx + 2, ty + 93, 0xFF6FBFB0, false);
    }

    private void drawStatus(GuiGraphicsExtractor g) {
        if (System.currentTimeMillis() < statusUntil && !status.isEmpty()) {
            int pt = paperTop();
            int w = this.font.width(status) + 12;
            int x = (this.width - w) / 2;
            int y = Math.min(this.height - 16, pt + PAPER_H + 12);
            g.fill(x - 1, y - 1, x + w + 1, y + 13, Theme.BORDER);
            g.fill(x, y, x + w, y + 12, 0xF0202830);
            g.text(this.font, Component.literal(status), x + 6, y + 2, statusBad ? Theme.RED : Theme.GREEN);
        }
    }
}
