package com.qualityclip.screen;

import com.qualityclip.QualityClipMod;
import com.qualityclip.compat.BookBridge;
import com.qualityclip.compat.Compat;
import com.qualityclip.compat.Safe;
import com.qualityclip.data.Clip;
import com.qualityclip.data.ClipStore;
import com.qualityclip.data.Schedule;
import com.qualityclip.data.Settings;
import com.qualityclip.data.Share;
import com.qualityclip.notify.EditorHook;
import com.qualityclip.notify.ChatState;
import com.qualityclip.notify.ClipIcon;
import com.qualityclip.notify.Fmt;
import com.qualityclip.notify.NotificationManager;
import com.qualityclip.notify.Placeholders;
import com.qualityclip.ui.Icons;
import com.qualityclip.ui.Imm;
import com.qualityclip.ui.Theme;
import com.qualityclip.ui.Time;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * "<Username>'s Clipboard": sidebar (Chat clips / Notes / Reminders), a settings gear, and a card grid.
 * Chat clips open in a popup; notes and reminders open their own editors. Everything auto-saves.
 * All custom drawing happens BEFORE super.extractRenderState, which paints the text boxes on top.
 */
public class ClipboardScreen extends Screen {
    public enum Cat {
        CHAT("Chat Clips", Icons.CHAT, Clip.TEXT, "Messages you can paste into chat", Theme.TEAL, "New chat clip"),
        NOTES("Notes", Icons.BOOK, Clip.BOOK, "Multi-page notes, just like a Book & Quill", Theme.GOLD, "New notes"),
        SIGNS("Signs", Icons.SIGN, Clip.SIGN, "Sign text you can paste onto a sign", 0xFFD9A066, "New sign"),
        REMINDERS("Reminders", Icons.BELL, Clip.REMINDER, "Get a notification at a time you choose", Theme.BLUE, "New reminder");

        final String label, sub, newLabel;
        final Icons icon;
        final String type;
        final int color;

        Cat(String label, Icons icon, String type, String sub, int color, String newLabel) {
            this.label = label;
            this.icon = icon;
            this.type = type;
            this.sub = sub;
            this.color = color;
            this.newLabel = newLabel;
        }
    }

    private enum Mode { LIST, VIEW, SCHEDULE }

    private static Cat category = Cat.CHAT;

    private static final int CARD_H = 72;
    private static final int GAP = 6;
    private static final int MIN_CARD_W = 108;
    private static final int MAX_CLIP = 256;
    private static final int MAX_NAME = 32;

    private final Screen parent;
    private final String headingText;
    private final Imm imm = new Imm();
    private final Imm backdrop = new Imm();
    private boolean dead;

    private Mode mode = Mode.LIST;
    private Clip current;
    private boolean newClip;
    private boolean confirmDelete;
    private int scroll;

    private EditBox nameBox;
    private EditBox textBox;

    private Schedule.Mode dMode = Schedule.Mode.ONCE;
    private int dInterval = 15;
    private boolean dSound = true;
    private String message = "";
    private boolean messageBad = true;

    public ClipboardScreen(Screen parent) {
        super(Component.literal("Clipboard"));
        String user;
        try {
            user = Safe.userName();
        } catch (Throwable t) {
            user = "Player";
        }
        this.headingText = user + "'s Clipboard";
        this.parent = parent;
    }

    public static void selectCategory(Cat c) {
        category = c;
    }

    // ------------------------------------------------------------------ plumbing

    private void fail(Throwable t) {
        if (dead) return;
        dead = true;
        QualityClipMod.LOGGER.error("QualityClip screen closed after an error", t);
        try {
            ClipStore.save();
            Compat.setScreen(parent);
        } catch (Throwable ignored) {
        }
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    private boolean hostIsBook() {
        return EditorHook.kind(parent) == EditorHook.BOOK;
    }

    private boolean hostIsSign() {
        return EditorHook.kind(parent) == EditorHook.SIGN;
    }

    private String flashText = "";
    private boolean flashBad;
    private long flashUntil;
    private boolean confirmPaste;

    private void flash(String msg, boolean bad) {
        flashText = msg;
        flashBad = bad;
        flashUntil = System.currentTimeMillis() + 5000;
    }

    private void exportAll() {
        List<Clip> all = new ArrayList<>(ClipStore.clips());
        if (all.isEmpty()) {
            flash("Nothing to export yet.", true);
            return;
        }
        Path f = Share.export(all, "all");
        if (f == null) {
            flash("Couldn't write the export file.", true);
            return;
        }
        Safe.copy(f.toString());
        flash("Exported " + all.size() + " to " + f.getFileName() + " (path copied)", false);
    }

    private void exportOne(Clip c) {
        Path f = Share.export(List.of(c), c.name);
        if (f == null) {
            message = "Couldn't write the export file.";
            messageBad = true;
            return;
        }
        Safe.copy(f.toString());
        message = "Exported! Path copied: " + f.getFileName();
        messageBad = false;
    }

    private void importFolder() {
        int[] r = Share.importFolder();
        reportImport(r, true);
    }

    private void reportImport(int[] r, boolean fromFolder) {
        if (r[0] > 0) flash("Imported " + r[0] + (r[0] == 1 ? " clip" : " clips") + (r[1] > 0 ? " (" + r[1] + " file(s) failed)" : ""), false);
        else if (r[1] > 0) flash("That file couldn't be read as QualityClip data.", true);
        else flash(fromFolder ? "Nothing to import. Drop .json files onto this screen, or put them in config/qualityclip/imports" : "No clips found in that file.", true);
    }

    /** the game calls this when files are dragged onto the window */
    public void onFilesDrop(List<Path> paths) {
        try {
            reportImport(Share.importPaths(paths), false);
        } catch (Throwable t) {
            flash("Import failed.", true);
        }
    }

    private boolean hostIsChat() {
        return parent instanceof ChatScreen;
    }

    // ------------------------------------------------------------------ geometry

    private boolean wideSide() {
        return this.width >= 380;
    }

    private int sideW() {
        return wideSide() ? 116 : 30;
    }

    private int listTop() {
        return 58;
    }

    private int listBottom() {
        return this.height - 6;
    }

    private int cols() {
        int mainW = this.width - sideW();
        return Math.max(1, (mainW - 16 + GAP) / (MIN_CARD_W + GAP));
    }

    private int cardW() {
        int c = cols();
        return (this.width - sideW() - 16 - (c - 1) * GAP) / c;
    }

    private int cardX(int i) {
        return sideW() + 8 + (i % cols()) * (cardW() + GAP);
    }

    private int cardY(int i) {
        return listTop() + (i / cols()) * (CARD_H + GAP) - scroll;
    }

    private List<Clip> items() {
        List<Clip> out = new ArrayList<>();
        for (Clip c : ClipStore.clips()) {
            if (category.type.equals(c.type)) out.add(c);
        }
        out.sort((a, b) -> Boolean.compare(b.favorite, a.favorite));
        return out;
    }

    private void clampScroll() {
        int n = items().size() + 1;
        int rows = (n + cols() - 1) / cols();
        int contentH = rows * (CARD_H + GAP);
        int viewH = listBottom() - listTop();
        scroll = Math.max(0, Math.min(Math.max(0, contentH - viewH), scroll));
    }

    // ------------------------------------------------------------------ init

    @Override
    protected void init() {
        try {
            if (mode == Mode.VIEW) initView();
        } catch (Throwable t) {
            fail(t);
        }
    }

    private int popW() {
        return Math.min(300, this.width - 12);
    }

    private int popH(int want) {
        return Math.min(want, this.height - 8);
    }

    private int popX() {
        return (this.width - popW()) / 2;
    }

    private int popY(int ph) {
        return Math.max(4, (this.height - ph) / 2);
    }

    private void initView() {
        int ph = popH(222);
        int px = popX(), py = popY(ph), pw = popW();
        nameBox = addRenderableWidget(new EditBox(this.font, px + 10, py + 39, pw - 20, 16, Component.literal("Clip name")));
        nameBox.setMaxLength(MAX_NAME);
        nameBox.setValue(current.name);
        nameBox.setResponder(v -> {
            current.name = v;
            ClipStore.save();
        });
        textBox = addRenderableWidget(new EditBox(this.font, px + 10, py + 71, pw - 20, 16, Component.literal("Clip text")));
        textBox.setMaxLength(MAX_CLIP);
        textBox.setValue(current.content);
        textBox.setResponder(v -> {
            current.content = v;
            ClipStore.save();
        });
    }

    // ---------------------------------------------------------------- actions

    private void createClip() {
        Clip c = new Clip();
        c.type = category.type;
        if (c.isBook()) c.pages();
        if (c.isReminder()) c.name = "New Reminder";
        if (c.isSign()) {
            c.name = "New Sign";
            c.pages().add("");
        }
        ClipStore.add(c);
        openClip(c, true);
    }

    private void openClip(Clip c, boolean isNew) {
        confirmPaste = false;
        current = c;
        newClip = isNew;
        confirmDelete = false;
        message = "";
        if (c.isBook()) {
            current = null;
            Compat.setScreen(new NotebookScreen(c, this, parent, isNew));
            return;
        }
        if (c.isReminder()) {
            current = null;
            Compat.setScreen(new ReminderScreen(c, this, parent, isNew));
            return;
        }
        if (c.isSign()) {
            current = null;
            Compat.setScreen(new SignScreen(c, this, parent, isNew));
            return;
        }
        mode = Mode.VIEW;
        rebuild();
    }

    /** used by the notebook's Schedule button */
    public void openScheduleFor(Clip c) {
        current = c;
        newClip = false;
        openSchedule();
    }

    private void goList() {
        commit();
        current = null;
        mode = Mode.LIST;
        rebuild();
    }

    private void backToView() {
        message = "";
        if (current != null && (current.isBook() || current.isSign())) {
            Clip c = current;
            current = null;
            mode = Mode.LIST;
            Compat.setScreen(c.isSign() ? new SignScreen(c, this, parent, false) : new NotebookScreen(c, this, parent, false));
            return;
        }
        mode = Mode.VIEW;
        rebuild();
    }

    private void openSchedule() {
        Schedule s = current.schedule;
        dMode = s != null ? s.mode : Schedule.Mode.ONCE;
        dInterval = s != null ? s.intervalMinutes : 15;
        dSound = s == null || s.sound;
        message = "";
        mode = Mode.SCHEDULE;
        rebuild();
    }

    private void copyClip() {
        boolean ok = com.qualityclip.compat.Safe.copy(Placeholders.expand(current.content));
        message = ok ? "Copied to your clipboard!" : "Couldn't copy on this version.";
        messageBad = !ok;
    }

    private void useInChat() {
        ChatState.pending = Placeholders.expand(current.content);
        commit();
        Compat.setScreen(parent);
    }

    private void pasteInBook() {
        if (!confirmPaste) {
            confirmPaste = true;
            return;
        }
        confirmPaste = false;
        List<String> one = new ArrayList<>();
        one.add(Placeholders.expand(current.content));
        boolean[] ok = new boolean[1];
        String msg = BookBridge.paste(parent, one, ok);
        if (ok[0]) {
            EditorHook.say(msg);
            commit();
            Compat.setScreen(parent);
        } else {
            message = msg;
            messageBad = true;
        }
    }

    private void deleteClip() {
        if (!confirmDelete) {
            confirmDelete = true;
            return;
        }
        NotificationManager.removeForClip(current.id);
        ClipStore.remove(current);
        current = null;
        mode = Mode.LIST;
        rebuild();
    }

    private void startSchedule() {
        long now = System.currentTimeMillis();
        Schedule s = new Schedule();
        s.mode = dMode;
        s.intervalMinutes = dInterval;
        s.sound = dSound;
        s.nextFireMs = now + dInterval * 60_000L;
        if (dMode == Schedule.Mode.TODAY) {
            s.endMs = Time.endOfToday();
            if (s.nextFireMs > s.endMs) {
                message = "Not enough time left today for that interval!";
                messageBad = true;
                return;
            }
        }
        current.schedule = s;
        ClipStore.save();
        message = "";
    }

    private void clearSchedule() {
        current.schedule = null;
        NotificationManager.removeForClip(current.id);
        ClipStore.save();
        message = "";
    }

    private void commit() {
        if (current == null) return;
        if (current.name == null || current.name.isBlank()) current.name = "Untitled";
        if (newClip && current.isBlank() && Clip.DEFAULT_NAME.equals(current.name) && current.schedule == null) {
            ClipStore.remove(current);
            current = null;
            return;
        }
        ClipStore.save();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        try {
            int mx = (int) event.x(), my = (int) event.y();
            boolean left = Compat.isLeft(event.button());
            if ((left || Compat.isRight(event.button())) && imm.click(mx, my, left)) return true;
            return super.mouseClicked(event, doubleClick);
        } catch (Throwable t) {
            fail(t);
            return true;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (imm.drag((int) event.x())) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (imm.release()) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mode == Mode.LIST) {
            scroll -= (int) (scrollY * 18);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        switch (mode) {
            case SCHEDULE -> backToView();
            case VIEW -> goList();
            default -> {
                commit();
                Compat.setScreen(parent);
            }
        }
    }

    // -------------------------------------------------------------- rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (dead) return;
        try {
            if (mode == Mode.LIST) {
                imm.begin();
                drawList(g, imm, mouseX, mouseY);
            } else {
                backdrop.begin();
                drawList(g, backdrop, -1000, -1000);
                g.fill(0, 0, this.width, this.height, 0xB0080B0F);
                imm.begin();
                if (mode == Mode.VIEW) drawView(g, mouseX, mouseY);
                else drawSchedule(g, mouseX, mouseY);
            }
            super.extractRenderState(g, mouseX, mouseY, delta);
            imm.drawTip(g, this.font, this.width);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void drawList(GuiGraphicsExtractor g, Imm im, int mx, int my) {
        int W = this.width, H = this.height, sw = sideW();
        boolean wide = wideSide();

        // backgrounds
        g.fill(0, 0, W, H, 0xF2141A20);
        g.fill(0, 0, sw, H, 0xFF0F1419);
        g.fill(sw - 1, 0, sw, H, Theme.BORDER);

        // sidebar header
        ClipIcon.draw(g, wide ? 9 : (sw - 12) / 2, 8);
        if (wide) g.text(this.font, Component.literal("QualityClip"), 26, 11, Theme.GOLD);
        g.fill(6, 26, sw - 7, 27, Theme.LINE);

        // sidebar entries: Chat, Notes, Signs, Quick Clips on top; Reminders at the bottom
        Cat[] topCats = {Cat.CHAT, Cat.NOTES, Cat.SIGNS};
        for (int i = 0; i < topCats.length; i++) sideRow(g, im, mx, my, 32 + i * 26, topCats[i].label, topCats[i].icon, topCats[i].color, topCats[i] == category, topCats[i]);
        sideRow(g, im, mx, my, 32 + topCats.length * 26, "Quick Clips", Icons.BOLT, Theme.GOLD, false, null);
        sideRow(g, im, mx, my, H - 26 - 8 - 24, Cat.REMINDERS.label, Cat.REMINDERS.icon, Cat.REMINDERS.color, category == Cat.REMINDERS, Cat.REMINDERS);
        g.fill(6, H - 26 - 8 - 24 - 5, sw - 7, H - 26 - 8 - 24 - 4, Theme.LINE);

        // done
        if (wide) {
            im.btn(g, this.font, mx, my, 6, H - 26, sw - 12, 20, "Done", Icons.CHECK, Imm.NORMAL, true, null, this::onClose);
        } else {
            im.iconBtn(g, this.font, mx, my, (sw - 20) / 2, H - 26, 20, Icons.CHECK, Imm.NORMAL, true, false, "Done", this::onClose);
        }

        // header
        g.text(this.font, heading_bold(), sw + 8, 9, Theme.TEXT);
        im.iconBtn(g, this.font, mx, my, W - 26, 3, 20, Icons.GEAR, Imm.NORMAL, true, false, "Settings",
                () -> Compat.setScreen(new SettingsScreen(this)));
        im.iconBtn(g, this.font, mx, my, W - 50, 3, 20, Icons.EXPORT, Imm.NORMAL, true, false,
                "Export everything to a .json file", this::exportAll);
        im.iconBtn(g, this.font, mx, my, W - 74, 3, 20, Icons.IMPORT, Imm.NORMAL, true, false,
                "Import clips: drop .json files onto this screen, or press to read the imports folder", this::importFolder);
        if (hostIsBook() || hostIsChat() || hostIsSign()) {
            String hint = hostIsBook() ? "Opened from your book" : hostIsSign() ? "Opened from a sign" : "Opened from chat";
            int hw = this.font.width(hint);
            if (sw + 8 + this.font.width(headingText) + hw + 90 < W - 30) {
                g.text(this.font, Component.literal(hint), W - 80 - hw, 9, Theme.TEAL);
            }
        }
        g.fill(sw, 26, W, 27, Theme.LINE);

        // section heading
        List<Clip> items = items();
        g.text(this.font, Component.literal(category.label), sw + 8, 33, category.color);
        String cnt = items.size() + (items.size() == 1 ? " clip" : " clips");
        if (category == Cat.REMINDERS) cnt = items.size() + (items.size() == 1 ? " reminder" : " reminders");
        if (category == Cat.NOTES) cnt = items.size() + (items.size() == 1 ? " notebook" : " notebooks");
        g.text(this.font, Component.literal(cnt), sw + 8 + this.font.width(category.label) + 8, 33, Theme.FAINT);
        String sub = hostIsBook() && category == Cat.NOTES ? "Open one and press the paste button to fill your book"
                : hostIsSign() && category == Cat.SIGNS ? "Open one and press the paste button to fill your sign" : category.sub;
        if (System.currentTimeMillis() < flashUntil && !flashText.isEmpty()) {
            g.text(this.font, Component.literal(Theme.fit(this.font, flashText, W - sw - 16)), sw + 8, 44, flashBad ? Theme.RED : Theme.GREEN);
        } else {
            g.text(this.font, Component.literal(Theme.fit(this.font, sub, W - sw - 16)), sw + 8, 44, Theme.MUTED);
        }

        // cards
        clampScroll();
        int top = listTop(), bottom = listBottom();
        g.enableScissor(sw, top - 2, W, bottom);
        try {
            drawNewTile(g, im, mx, my, 0);
            for (int i = 0; i < items.size(); i++) {
                drawCard(g, im, items.get(i), i + 1, mx, my);
            }
        } finally {
            g.disableScissor();
        }
        if (items.isEmpty() && cols() >= 1) {
            int ty = top + CARD_H + 12;
            if (ty < bottom - 10) {
                g.text(this.font, Component.literal(Theme.fit(this.font, "Nothing here yet. Click the tile to make your first one!", W - sw - 16)),
                        sw + 8, ty, Theme.FAINT);
            }
        }
        // scrollbar
        int n = items.size() + 1;
        int rows = (n + cols() - 1) / cols();
        int contentH = rows * (CARD_H + GAP), viewH = bottom - top;
        if (contentH > viewH) {
            int trackH = viewH;
            int knobH = Math.max(14, trackH * viewH / contentH);
            int knobY = top + (trackH - knobH) * scroll / Math.max(1, contentH - viewH);
            g.fill(W - 4, top, W - 2, bottom, 0xFF0F1419);
            g.fill(W - 4, knobY, W - 2, knobY + knobH, 0xFF4A5663);
        }
    }

    private Component heading_bold() {
        return Component.literal(headingText).withStyle(s -> s.withBold(Boolean.TRUE));
    }

    private void imTip(Imm im, GuiGraphicsExtractor g, String text, int mx, int my) {
        // sidebar tooltip in compact mode (drawn immediately, above everything else in the list)
        com.qualityclip.notify.Tip.draw(g, this.font, text, mx + 10, my - 6, this.width);
    }

    private void sideRow(GuiGraphicsExtractor g, Imm im, int mx, int my, int ry, String label, Icons icon, int color,
                         boolean sel, Cat cat) {
        int sw = sideW();
        boolean wide = wideSide();
        int rw = sw - 8;
        boolean hover = Imm.over(mx, my, 4, ry, rw, 24);
        if (sel) {
            g.fill(4, ry, 4 + rw, ry + 24, 0xFF202A33);
            g.fill(4, ry, 6, ry + 24, color);
        } else if (hover) {
            g.fill(4, ry, 4 + rw, ry + 24, 0xFF182027);
        }
        int ix = wide ? 12 : (sw - Icons.SIZE) / 2 + 1;
        icon.draw(g, ix, ry + 8, sel ? Theme.TEXT : Theme.MUTED, color);
        if (wide) {
            g.text(this.font, Component.literal(Theme.fit(this.font, label, cat == null ? rw - 28 : rw - 44)), 26, ry + 8, sel ? Theme.TEXT : Theme.MUTED);
            if (cat != null) {
                int count = 0;
                for (Clip k : ClipStore.clips()) if (cat.type.equals(k.type)) count++;
                String cs = String.valueOf(count);
                g.text(this.font, Component.literal(cs), 4 + rw - 6 - this.font.width(cs), ry + 8, Theme.FAINT);
            }
        }
        final Cat cc = cat;
        im.area(mx, my, 4, ry, rw, 24, () -> {
            if (cc == null) {
                Compat.setScreen(new QuickClipsScreen(this));
            } else {
                category = cc;
                scroll = 0;
            }
        }, null);
        if (hover && !wide) com.qualityclip.notify.Tip.draw(g, this.font, label, mx + 10, my - 6, this.width);
    }

    private boolean visibleInView(int y, int h) {
        return y + h > listTop() && y < listBottom();
    }

    private void drawNewTile(GuiGraphicsExtractor g, Imm im, int mx, int my, int idx) {
        int x = cardX(idx), y = cardY(idx), w = cardW();
        if (!visibleInView(y, CARD_H)) return;
        int cy0 = Math.max(y, listTop()), cy1 = Math.min(y + CARD_H, listBottom());
        boolean hover = Imm.over(mx, my, x, cy0, w, cy1 - cy0);
        int edge = hover ? category.color : 0xFF37424D;
        g.fill(x, y, x + w, y + CARD_H, 0xFF11171C);
        // dashed border
        for (int dx = 0; dx < w; dx += 6) {
            int len = Math.min(3, w - dx);
            g.fill(x + dx, y, x + dx + len, y + 1, edge);
            g.fill(x + dx, y + CARD_H - 1, x + dx + len, y + CARD_H, edge);
        }
        for (int dy = 0; dy < CARD_H; dy += 6) {
            int len = Math.min(3, CARD_H - dy);
            g.fill(x, y + dy, x + 1, y + dy + len, edge);
            g.fill(x + w - 1, y + dy, x + w, y + dy + len, edge);
        }
        Icons.PLUS.draw(g, x + (w - 9) / 2, y + CARD_H / 2 - 12, hover ? Theme.TEXT : category.color);
        String label = Theme.fit(this.font, category.newLabel, w - 10);
        g.text(this.font, Component.literal(label), x + (w - this.font.width(label)) / 2, y + CARD_H / 2 + 5,
                hover ? Theme.TEXT : Theme.MUTED);
        im.area(mx, my, x, cy0, w, cy1 - cy0, this::createClip, null);
    }

    private void drawCard(GuiGraphicsExtractor g, Imm im, Clip c, int idx, int mx, int my) {
        int x = cardX(idx), y = cardY(idx), w = cardW();
        if (!visibleInView(y, CARD_H)) return;
        int cy0 = Math.max(y, listTop()), cy1 = Math.min(y + CARD_H, listBottom());
        boolean hover = Imm.over(mx, my, x, cy0, w, cy1 - cy0);
        int accent = category.color;

        Theme.rr(g, x - 1, y - 1, w + 2, CARD_H + 2, hover ? 0xFFB9C7D4 : Theme.BORDER);
        g.fill(x, y, x + w, y + CARD_H, hover ? 0xFF26303A : 0xFF1E262E);
        g.fill(x, y, x + w, y + 2, accent);

        int inner = w - 16;
        g.text(this.font, Component.literal(Theme.fit(this.font, c.name, inner - 14)), x + 8, y + 7, Theme.TEXT);
        g.fill(x + 8, y + 19, x + w - 8, y + 20, Theme.LINE);

        if (c.isReminder()) {
            drawReminderBody(g, c, x, y, w);
        } else if (c.isSign()) {
            String[] sl = c.pages().get(0).split("\n", -1);
            for (int j = 0; j < 4; j++) {
                String t = j < sl.length ? sl[j] : "";
                if (!t.isEmpty()) g.text(this.font, Component.literal(Theme.fit(this.font, t, inner)), x + 8, y + 24 + j * 10, 0xFFD9B26E);
            }
            int fy = y + CARD_H - 15;
            g.fill(x + 8, fy - 2, x + w - 8, fy - 1, 0xFF2A333C);
            Icons.SIGN.draw(g, x + 8, fy + 1, Theme.MUTED);
            g.text(this.font, Component.literal(c.pages().size() > 1 && !c.pages().get(1).isBlank() ? "Front + back" : "Front"), x + 20, fy + 1, Theme.MUTED);
            if (c.schedule != null) {
                Icons.BELL.draw(g, x + w - 17, fy + 1, Theme.GREEN, Theme.GREEN);
            }
        } else {
            String body = c.isBook() ? Fmt.strip(c.pages().get(0)) : c.content;
            if (body.isBlank()) {
                g.text(this.font, Component.literal("(empty)"), x + 8, y + 24, Theme.FAINT);
            } else {
                List<String> lines = wrap(body, inner);
                int n = Math.min(3, lines.size());
                for (int j = 0; j < n; j++) {
                    String line = lines.get(j);
                    if (j == 2 && lines.size() > 3) line = Theme.fit(this.font, line + "...", inner);
                    g.text(this.font, Component.literal(line), x + 8, y + 24 + j * 10, 0xFFC3CDD6);
                }
            }
            // footer
            int fy = y + CARD_H - 15;
            g.fill(x + 8, fy - 2, x + w - 8, fy - 1, 0xFF2A333C);
            if (c.isBook()) {
                String p = c.pages().size() + (c.pages().size() == 1 ? " page" : " pages");
                Icons.BOOK.draw(g, x + 8, fy + 1, Theme.MUTED);
                g.text(this.font, Component.literal(p), x + 20, fy + 1, Theme.MUTED);
            } else {
                String chip = c.schedule == null ? "" : (c.schedule.mode == Schedule.Mode.ONCE ? "Once" : c.schedule.intervalMinutes + "m");
                int chipW = chip.isEmpty() ? 0 : this.font.width(chip) + Icons.SIZE + 8;
                String p = c.content.length() + (inner - chipW > 70 ? " chars" : "");
                g.text(this.font, Component.literal(Theme.fit(this.font, p, inner - chipW)), x + 8, fy + 1, Theme.FAINT);
            }
            if (c.schedule != null) {
                String cs = c.schedule.mode == Schedule.Mode.ONCE ? "Once" : c.schedule.intervalMinutes + "m";
                int cw = this.font.width(cs) + Icons.SIZE + 3;
                Icons.BELL.draw(g, x + w - 8 - cw, fy + 1, Theme.GREEN, Theme.GREEN);
                g.text(this.font, Component.literal(cs), x + w - 8 - cw + Icons.SIZE + 3, fy + 1, Theme.GREEN);
            }
        }
        final Clip cc = c;
        im.area(mx, my, x, cy0, w, cy1 - cy0, () -> openClip(cc, false), null);
        // favourite star (registered after the card so it wins the click)
        int sx = x + w - 17, sy = y + 6;
        boolean sh = Imm.over(mx, my, sx - 2, sy - 2, 14, 14) && sy >= listTop() - 4 && sy < listBottom();
        (c.favorite ? Icons.STAR : Icons.STAR_OFF).draw(g, sx, sy, c.favorite ? Theme.GOLD : (sh ? Theme.TEXT : Theme.FAINT), Theme.GOLD);
        if (y + 6 >= listTop() && y + 16 <= listBottom()) {
            im.area(mx, my, sx - 2, sy - 2, 14, 14, () -> {
                cc.favorite = !cc.favorite;
                ClipStore.save();
            }, null);
        }
        if (sh) com.qualityclip.notify.Tip.draw(g, this.font, c.favorite ? "Remove from favorites" : "Add to favorites", mx - 100, my + 10, this.width);
    }

    private void drawReminderBody(GuiGraphicsExtractor g, Clip c, int x, int y, int w) {
        int inner = w - 16;
        boolean h24 = Settings.get().clock24;
        String status;
        int sc;
        if (c.schedule != null) {
            long due = c.schedule.nextFireMs;
            g.text(this.font, Component.literal(Theme.fit(this.font, Time.when(due, h24), inner)), x + 8, y + 24, Theme.GOLD);
            g.text(this.font, Component.literal(Theme.fit(this.font, Time.relative(due), inner)), x + 8, y + 35, Theme.MUTED);
            status = "NONE".equals(c.repeat) ? "Waiting" : ("WEEKLY".equals(c.repeat) ? "Weekly" : "Daily");
            sc = Theme.BLUE;
        } else if (c.doneMs > 0) {
            g.text(this.font, Component.literal(Theme.fit(this.font, Time.when(c.doneMs, h24), inner)), x + 8, y + 24, Theme.MUTED);
            g.text(this.font, Component.literal("Fired " + Time.relative(c.doneMs)), x + 8, y + 35, Theme.FAINT);
            status = "Done";
            sc = Theme.GREEN;
        } else {
            g.text(this.font, Component.literal("No time set yet"), x + 8, y + 24, Theme.RED);
            status = "Not set";
            sc = Theme.RED;
        }
        if (!c.content.isBlank()) {
            g.text(this.font, Component.literal(Theme.fit(this.font, c.content.replace('\n', ' '), inner)), x + 8, y + 47, 0xFFC3CDD6);
        }
        int fy = y + CARD_H - 15;
        g.fill(x + 8, fy - 2, x + w - 8, fy - 1, 0xFF2A333C);
        Icons.BELL.draw(g, x + 8, fy + 1, sc, sc);
        g.text(this.font, Component.literal(status), x + 20, fy + 1, sc);
    }

    // ---------------------------------------------------------- popup: chat clip

    private void drawPopupFrame(GuiGraphicsExtractor g, int px, int py, int pw, int ph, String title, String subtitle,
                                Runnable close, int mx, int my) {
        Theme.panel(g, px, py, pw, ph);
        g.fill(px, py, px + pw, py + 22, Theme.PANEL_HI);
        g.fill(px, py + 22, px + pw, py + 23, Theme.BORDER);
        g.text(this.font, Component.literal(title).withStyle(s -> s.withBold(Boolean.TRUE)), px + 10, py + 7, Theme.TEXT);
        if (subtitle != null) {
            int tw = this.font.width(title) + 20;
            g.text(this.font, Component.literal(Theme.fit(this.font, subtitle, pw - tw - 36)), px + tw, py + 7, Theme.FAINT);
        }
        imm.iconBtn(g, this.font, mx, my, px + pw - 20, py + 3, 16, Icons.CROSS, Imm.GHOST, true, false, "Close", close);
    }

    private void drawView(GuiGraphicsExtractor g, int mx, int my) {
        int ph = popH(222), pw = popW();
        int px = popX(), py = popY(ph);
        drawPopupFrame(g, px, py, pw, ph, "Chat clip", null, this::goList, mx, my);

        g.text(this.font, Component.literal("Name"), px + 10, py + 29, Theme.MUTED);
        g.text(this.font, Component.literal("Text to paste"), px + 10, py + 61, Theme.MUTED);
        String count = current.content.length() + "/" + MAX_CLIP;
        g.text(this.font, Component.literal(count), px + pw - 10 - this.font.width(count), py + 61,
                current.content.length() >= MAX_CLIP ? Theme.RED : Theme.FAINT);
        boolean hasPh = Placeholders.has(current.content);

        int btnY1 = py + ph - 54;
        int boxY = py + 105;
        int boxH = btnY1 - 16 - boxY;
        if (boxH >= 24) {
            g.text(this.font, Component.literal(hasPh ? "Preview (placeholders filled in)" : "Preview"), px + 10, boxY - 11, hasPh ? Theme.TEAL : Theme.MUTED);
            Theme.inset(g, px + 10, boxY, pw - 20, boxH);
            if (current.content.isEmpty()) {
                g.text(this.font, Component.literal("(empty - tip: try {x} {y} {z} or {biome})"), px + 15, boxY + 5, Theme.FAINT);
            } else {
                List<String> lines = wrap(Placeholders.expand(current.content), pw - 30);
                int max = Math.max(1, (boxH - 6) / 10);
                for (int i = 0; i < Math.min(max, lines.size()); i++) {
                    String line = lines.get(i);
                    if (i == max - 1 && lines.size() > max) line = Theme.fit(this.font, line + "...", pw - 30);
                    g.text(this.font, Component.literal(line), px + 15, boxY + 4 + i * 10, Theme.TEXT);
                }
            }
        }
        if (confirmPaste) {
            g.text(this.font, Component.literal(Theme.fit(this.font, "This REPLACES your whole book. Click again to confirm.", pw - 20)),
                    px + 10, btnY1 - 11, Theme.RED);
        } else if (!message.isEmpty()) {
            g.text(this.font, Component.literal(Theme.fit(this.font, message, pw - 20)), px + 10, btnY1 - 11,
                    messageBad ? Theme.RED : Theme.GREEN);
        }

        // buttons
        int bw = pw - 20;
        boolean three = hostIsChat() || hostIsBook();
        int n = three ? 3 : 2;
        int cw = (bw - (n - 1) * 4) / n;
        imm.btn(g, this.font, mx, my, px + 10, btnY1, cw, 20, "Schedule", Icons.BELL,
                current.schedule != null ? Imm.PRIMARY : Imm.NORMAL, true, "Click to set schedule", this::openSchedule);
        imm.btn(g, this.font, mx, my, px + 10 + cw + 4, btnY1, cw, 20, "Copy", Icons.COPY, Imm.NORMAL, true,
                "Copy to your computer's clipboard", this::copyClip);
        if (hostIsChat()) {
            imm.btn(g, this.font, mx, my, px + 10 + 2 * (cw + 4), btnY1, cw, 20, "Use in chat", Icons.CHAT, Imm.PRIMARY,
                    true, "Put this clip in your chat box", this::useInChat);
        } else if (hostIsBook()) {
            imm.btn(g, this.font, mx, my, px + 10 + 2 * (cw + 4), btnY1, cw, 20, confirmPaste ? "Replace?" : "Paste", Icons.PASTE_IN,
                    confirmPaste ? Imm.DANGER : Imm.PRIMARY, true, "Paste into your open book (replaces its pages)", this::pasteInBook);
        }
        int btnY2 = py + ph - 28;
        int hw = (bw - 4) / 2;
        int w3 = (bw - 8) / 3;
        imm.btn(g, this.font, mx, my, px + 10, btnY2, w3, 20, confirmDelete ? "Sure?" : "Delete", Icons.TRASH,
                Imm.DANGER, true, null, this::deleteClip);
        imm.btn(g, this.font, mx, my, px + 14 + w3, btnY2, w3, 20, "Export", Icons.EXPORT, Imm.NORMAL, true,
                "Save this clip as a .json file you can share", () -> exportOne(current));
        imm.btn(g, this.font, mx, my, px + 18 + 2 * w3, btnY2, bw - 8 - 2 * w3, 20, "Done", Icons.CHECK, Imm.NORMAL, true, null, this::goList);
    }

    // ---------------------------------------------------------- popup: schedule

    private void drawSchedule(GuiGraphicsExtractor g, int mx, int my) {
        int ph = popH(196), pw = popW();
        int px = popX(), py = popY(ph);
        drawPopupFrame(g, px, py, pw, ph, "Schedule", current.name, this::backToView, mx, my);
        int inner = pw - 20;

        String[] modes = {"Once", "Today", "Forever"};
        imm.seg(g, this.font, mx, my, px + 10, py + 30, inner, 18, modes, dMode.ordinal(), i -> {
            dMode = Schedule.Mode.values()[i];
            message = "";
        });
        String desc = switch (dMode) {
            case ONCE -> "Remind me one time.";
            case TODAY -> "Keep reminding me until midnight tonight.";
            case FOREVER -> "Keep reminding me until I clear it.";
        };
        g.text(this.font, Component.literal(Theme.fit(this.font, desc, inner)), px + 10, py + 53, Theme.MUTED);

        String lab = dMode == Schedule.Mode.ONCE ? "Remind me in" : "Remind me every";
        g.text(this.font, Component.literal(lab), px + 10, py + 71, Theme.TEXT);
        String val = dInterval + " min";
        g.text(this.font, Component.literal(val), px + pw - 10 - this.font.width(val), py + 71, Theme.GOLD);
        imm.slider(g, this.font, mx, my, px + 14, py + 82, inner - 8, dInterval, 5, 60, 5, v -> {
            dInterval = v;
            message = "";
        });

        g.text(this.font, Component.literal("Play a sound"), px + 10, py + 106, Theme.TEXT);
        imm.toggle(g, mx, my, px + pw - 10 - 24, py + 104, dSound, () -> dSound = !dSound);

        // status
        int sy = py + 122;
        if (!message.isEmpty()) {
            g.text(this.font, Component.literal(Theme.fit(this.font, message, inner)), px + 10, sy, Theme.RED);
        } else if (current.schedule != null) {
            Schedule s = current.schedule;
            g.text(this.font, Component.literal("Next reminder " + Time.relative(s.nextFireMs)), px + 10, sy, Theme.GREEN);
            String ends = switch (s.mode) {
                case ONCE -> "Then it turns itself off.";
                case TODAY -> "Stops at midnight tonight.";
                case FOREVER -> "Repeats every " + s.intervalMinutes + " min until cleared.";
            };
            g.text(this.font, Component.literal(Theme.fit(this.font, ends, inner)), px + 10, sy + 11, Theme.MUTED);
        } else {
            g.text(this.font, Component.literal("No schedule yet. Pick your options, then press Start."), px + 10, sy, Theme.FAINT);
        }

        boolean active = current.schedule != null;
        int by = py + ph - 26;
        int w3 = (inner - 8) / 3;
        imm.btn(g, this.font, mx, my, px + 10, by, w3, 20, active ? "Update" : "Start", Icons.CHECK, Imm.PRIMARY, true, null, this::startSchedule);
        imm.btn(g, this.font, mx, my, px + 14 + w3, by, w3, 20, "Clear", Icons.TRASH, Imm.DANGER, active, null, this::clearSchedule);
        imm.btn(g, this.font, mx, my, px + 18 + 2 * w3, by, inner - 8 - 2 * w3, 20, "Back", Icons.BACK, Imm.NORMAL, true, null, this::backToView);
    }

    // ---------------------------------------------------------------- helpers

    private List<String> wrap(String text, int maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.replaceAll("[\\r\\n]+", " ").split(" ")) {
            if (word.isEmpty()) continue;
            String tryLine = cur.length() == 0 ? word : cur + " " + word;
            if (this.font.width(tryLine) <= maxW) {
                cur.setLength(0);
                cur.append(tryLine);
                continue;
            }
            if (cur.length() > 0) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            String rest = word;
            while (this.font.width(rest) > maxW && rest.length() > 1) {
                int cut = rest.length() - 1;
                while (cut > 1 && this.font.width(rest.substring(0, cut)) > maxW) cut--;
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            cur.append(rest);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }
}
