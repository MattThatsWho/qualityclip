package com.qualityclip.notify;

import com.qualityclip.compat.Compat;
import com.qualityclip.compat.Sounds;
import com.qualityclip.data.Clip;
import com.qualityclip.data.ClipStore;
import com.qualityclip.data.Settings;
import com.qualityclip.screen.ClipboardScreen;
import com.qualityclip.screen.NotebookScreen;
import com.qualityclip.screen.ReminderScreen;
import com.qualityclip.screen.SignScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Reminders. On the HUD they appear as toasts (style chosen in Settings). They stay "pending" until you
 * deal with them in the chat reminder panel (click = use it, X = dismiss, Clear All).
 * One entry per clip: a repeating reminder bumps a counter instead of piling up.
 */
public final class NotificationManager {
    public static final String TEST_ID = "__test__";

    // chat panel geometry
    private static final int PANEL_W = 196;
    private static final int HEADER_H = 20;
    private static final int ROW_H = 26;

    private static final class Note {
        final String clipId;
        int count = 1;
        long lastMs = System.currentTimeMillis();
        long hudStart = -1;

        Note(String clipId) {
            this.clipId = clipId;
        }
    }

    private static final class Ping {
        int delay;
        final float pitch;

        Ping(int delay, float pitch) {
            this.delay = delay;
            this.pitch = pitch;
        }
    }

    private static final class Info {
        final String title, msg;
        final long start = System.currentTimeMillis();

        Info(String title, String msg) {
            this.title = title;
            this.msg = msg;
        }
    }

    private static final List<Note> NOTES = new ArrayList<>();
    private static final List<Info> INFOS = new ArrayList<>();
    private static final List<Ping> PINGS = new ArrayList<>();
    private static int scroll = 0;

    private NotificationManager() {}

    public static boolean hasPending() {
        return !NOTES.isEmpty() || !INFOS.isEmpty();
    }

    /** a short one-off toast ("Saved: 120, 64, -30") */
    public static void info(String title, String msg) {
        INFOS.add(new Info(title, msg));
        if (INFOS.size() > 3) INFOS.remove(0);
    }

    /** remind me again later */
    public static void snooze(String clipId) {
        Clip c = ClipStore.find(clipId);
        if (c != null) {
            c.snoozeMs = System.currentTimeMillis() + Settings.get().snoozeMinutes * 60_000L;
            ClipStore.save();
        }
        removeForClip(clipId);
    }

    public static int pendingCount() {
        return NOTES.size();
    }

    // ------------------------------------------------------------------ adding / removing

    public static void push(Clip clip, boolean sound) {
        push(clip.id, sound);
    }

    public static void pushTest() {
        push(TEST_ID, true);
    }

    private static void push(String id, boolean sound) {
        Note existing = null;
        for (Note n : NOTES) {
            if (n.clipId.equals(id)) existing = n;
        }
        if (existing != null) {
            existing.count++;
            existing.lastMs = System.currentTimeMillis();
            existing.hudStart = -1; // slide it in again
        } else {
            NOTES.add(new Note(id));
        }
        if (sound && Settings.get().sound) {
            switch (Settings.get().style) {
                case STANDARD -> PINGS.add(new Ping(0, 1.0F));
                case BOLD -> {
                    PINGS.add(new Ping(0, 1.2F));
                    PINGS.add(new Ping(7, 1.5F));
                }
                case SUPER -> {
                    PINGS.add(new Ping(0, 0.9F));
                    PINGS.add(new Ping(5, 1.2F));
                    PINGS.add(new Ping(10, 1.6F));
                    PINGS.add(new Ping(15, 2.0F));
                }
            }
        }
    }

    public static void removeForClip(String clipId) {
        NOTES.removeIf(n -> n.clipId.equals(clipId));
    }

    public static void clearAll() {
        NOTES.clear();
        scroll = 0;
    }

    /** called every client tick */
    public static void tick() {
        if (PINGS.isEmpty()) return;
        Iterator<Ping> it = PINGS.iterator();
        while (it.hasNext()) {
            Ping p = it.next();
            if (p.delay-- <= 0) {
                it.remove();
                try {
                    Sounds.ping(p.pitch);
                } catch (Throwable ignored) {
                    // sound is a nice-to-have; never let it break a reminder
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String nameOf(Note n) {
        if (TEST_ID.equals(n.clipId)) return "Example Clip";
        Clip c = ClipStore.find(n.clipId);
        return c == null ? null : c.name;
    }

    private static int kind(Note n) {
        Clip c = TEST_ID.equals(n.clipId) ? null : ClipStore.find(n.clipId);
        if (c == null) return TEST_ID.equals(n.clipId) ? Toasts.TEXT : Toasts.TEXT;
        return c.isReminder() ? Toasts.REMINDER : c.isSign() ? Toasts.SIGN : c.isBook() ? Toasts.BOOK : Toasts.TEXT;
    }

    private static String ago(long ms) {
        long s = Math.max(0, (System.currentTimeMillis() - ms) / 1000);
        if (s < 45) return "just now";
        long m = s / 60;
        if (m < 60) return Math.max(1, m) + " min ago";
        return (m / 60) + " h ago";
    }

    // ------------------------------------------------------------------ HUD toasts

    private static int renderInfos(GuiGraphicsExtractor g, Font font, int sw, int y) {
        long now = System.currentTimeMillis();
        INFOS.removeIf(i -> now - i.start > 4200);
        for (Info i : INFOS) {
            long age = now - i.start;
            double k = Math.min(Math.min(1.0, age / 350.0), Math.min(1.0, (4200 - age) / 350.0));
            double ease = 1.0 - Math.pow(1.0 - k, 3);
            int w = 160;
            Toasts.infoToast(g, font, sw - w - 6 + (int) ((1.0 - ease) * (w + 10)), y, w, i.title, i.msg);
            y += 36;
        }
        return y;
    }

    public static void renderHud(GuiGraphicsExtractor g, Font font, int sw, int sh) {
        if (NOTES.isEmpty()) {
            if (!INFOS.isEmpty()) renderInfos(g, font, sw, 6);
            return;
        }
        Settings cfg = Settings.get();
        long now = System.currentTimeMillis();
        long dur = cfg.toastSeconds * 1000L;
        int maxShown = cfg.style == Settings.NotifyStyle.SUPER ? 1 : 3;
        int w = Toasts.width(cfg.style, sw);
        int h = Toasts.height(cfg.style);
        int stackY = 6;
        int shown = 0;

        for (int i = NOTES.size() - 1; i >= 0 && shown < maxShown; i--) {
            Note n = NOTES.get(i);
            String name = nameOf(n);
            if (name == null) {
                NOTES.remove(i);
                continue;
            }
            if (n.hudStart < 0) n.hudStart = now;
            long age = now - n.hudStart;
            if (age > dur) continue;

            double in = Math.min(1.0, age / 450.0);
            double out = Math.min(1.0, (dur - age) / 450.0);
            double k = Math.min(in, out);
            double ease = 1.0 - Math.pow(1.0 - k, 3);

            int x, y;
            if (cfg.style == Settings.NotifyStyle.SUPER) {
                x = (sw - w) / 2;
                y = stackY - (int) ((1.0 - ease) * (h + 10));
            } else {
                x = sw - w - 6 + (int) ((1.0 - ease) * (w + 10));
                y = stackY;
            }
            if (cfg.style == Settings.NotifyStyle.SUPER && shown == 0) {
                Toasts.edgeFlash(g, sw, sh, age, dur);
            }
            Toasts.draw(g, font, cfg.style, x, y, w, name, n.count, kind(n), now);
            stackY += h + 4;
            shown++;
        }
        if (!INFOS.isEmpty() && cfg.style != Settings.NotifyStyle.SUPER) renderInfos(g, font, sw, stackY);
        else if (!INFOS.isEmpty()) renderInfos(g, font, sw, 6);
    }

    // ------------------------------------------------------------------ chat panel

    private static int rowsFor(int sh) {
        return Math.max(2, Math.min(6, (sh / 2 - HEADER_H - 14) / ROW_H));
    }

    private static int visibleRows(int sh) {
        return Math.min(NOTES.size(), rowsFor(sh));
    }

    private static void clampScroll(int sh) {
        int max = Math.max(0, NOTES.size() - rowsFor(sh));
        scroll = Math.max(0, Math.min(max, scroll));
    }

    private static int panelX(int sw) {
        return sw - PANEL_W - 6;
    }

    private static int panelH(int sh) {
        int h = HEADER_H + visibleRows(sh) * ROW_H + 4;
        if (NOTES.size() > rowsFor(sh)) h += 12;
        return h;
    }

    private static void drawCross(GuiGraphicsExtractor g, int x, int y, int c) {
        for (int i = 0; i < 6; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, c);
            g.fill(x + 5 - i, y + i, x + 6 - i, y + i + 1, c);
        }
    }

    public static void renderPanel(GuiGraphicsExtractor g, Font font, int sw, int sh, int mx, int my) {
        if (NOTES.isEmpty()) return;
        clampScroll(sh);
        int x = panelX(sw), y = 6, h = panelH(sh);

        g.fill(x - 1, y - 1, x + PANEL_W + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + PANEL_W, y + h, 0xF0202020);
        g.fill(x, y, x + PANEL_W, y + HEADER_H, 0xFF333333);
        g.fill(x, y + HEADER_H - 1, x + PANEL_W, y + HEADER_H, 0xFF000000);
        g.text(font, Component.literal("Reminders (" + NOTES.size() + ")"), x + 8, y + 6, 0xFFFFFF55);

        // Clear All
        int cx = x + PANEL_W - 68, cy = y + 3, cw = 62, ch = 14;
        boolean hoverClear = mx >= cx && mx < cx + cw && my >= cy && my < cy + ch;
        g.fill(cx, cy, cx + cw, cy + ch, hoverClear ? 0xFFFFFFFF : 0xFF000000);
        g.fill(cx + 1, cy + 1, cx + cw - 1, cy + ch - 1, hoverClear ? 0xFF8B3A3A : 0xFF5A2A2A);
        g.text(font, Component.literal("Clear All"), cx + (cw - font.width("Clear All")) / 2, cy + 3, 0xFFFFFFFF);

        int rows = visibleRows(sh);
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= NOTES.size()) break;
            Note n = NOTES.get(idx);
            String name = nameOf(n);
            if (name == null) continue;
            int ry = y + HEADER_H + r * ROW_H;
            boolean rowHover = mx >= x && mx < x + PANEL_W && my >= ry && my < ry + ROW_H;
            boolean xHover = mx >= x + PANEL_W - 22 && mx < x + PANEL_W - 8 && my >= ry + 6 && my < ry + 20;
            if (rowHover) g.fill(x + 1, ry, x + PANEL_W - 1, ry + ROW_H, 0xFF3A3A3A);
            if (r > 0) g.fill(x + 6, ry, x + PANEL_W - 6, ry + 1, 0xFF2E2E2E);

            int kd = kind(n);
            Toasts.icon(g, kd, x + 8, ry + (kd == Toasts.TEXT ? 6 : 8), 1);
            int tx = x + 26, maxW = PANEL_W - 26 - 46;
            String line = Toasts.message(font, name, kd, maxW);
            g.text(font, Component.literal(line), tx, ry + 4, 0xFFFFFFFF);
            String sub = (n.count > 1 ? "x" + n.count + "  -  " : "") + ago(n.lastMs);
            g.text(font, Component.literal(Toasts.fit(font, sub, maxW)), tx, ry + 15, 0xFF999999);

            // snooze (clock) button
            int zx = x + PANEL_W - 38, zy = ry + 6;
            boolean zHover = mx >= zx && mx < zx + 14 && my >= zy && my < zy + 14;
            g.fill(zx, zy, zx + 14, zy + 14, zHover ? 0xFFFFFFFF : 0xFF000000);
            g.fill(zx + 1, zy + 1, zx + 13, zy + 13, zHover ? 0xFF3A7D74 : 0xFF4A4A4A);
            com.qualityclip.ui.Icons.CLOCK.draw(g, zx + 2, zy + 2, 0xFFFFFFFF, 0xFFFFFFFF);
            if (zHover) {
                com.qualityclip.notify.Tip.draw(g, font, "Snooze " + Settings.get().snoozeMinutes + " min", mx - 90, my + 12, sw);
            }

            // X
            int bx = x + PANEL_W - 22, by = ry + 6;
            g.fill(bx, by, bx + 14, by + 14, xHover ? 0xFFFFFFFF : 0xFF000000);
            g.fill(bx + 1, by + 1, bx + 13, by + 13, xHover ? 0xFFB03030 : 0xFF4A4A4A);
            drawCross(g, bx + 4, by + 4, 0xFFFFFFFF);
        }
        if (NOTES.size() > rowsFor(sh)) {
            int fy = y + HEADER_H + rows * ROW_H + 2;
            int more = NOTES.size() - rowsFor(sh);
            g.text(font, Component.literal("Scroll for more (+" + more + ")"), x + 8, fy, 0xFF888888);
        }
    }

    public static boolean overPanel(int sw, int sh, double mx, double my) {
        if (NOTES.isEmpty()) return false;
        int x = panelX(sw);
        return mx >= x && mx < x + PANEL_W && my >= 6 && my < 6 + panelH(sh);
    }

    public static boolean mouseScrolled(int sw, int sh, double mx, double my, double dy) {
        if (!overPanel(sw, sh, mx, my)) return false;
        scroll -= (int) Math.signum(dy);
        clampScroll(sh);
        return true;
    }

    /** @return true if the click was on the panel and has been handled */
    public static boolean mouseClicked(EditBox chatInput, Screen host, int sw, int sh, double mx, double my, int button) {
        if (!overPanel(sw, sh, mx, my)) return false;
        int x = panelX(sw), y = 6;
        int cx = x + PANEL_W - 68, cy = y + 3;
        if (Compat.isLeft(button) && mx >= cx && mx < cx + 62 && my >= cy && my < cy + 14) {
            clearAll();
            return true;
        }
        int rows = visibleRows(sh);
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= NOTES.size()) break;
            int ry = y + HEADER_H + r * ROW_H;
            if (my < ry || my >= ry + ROW_H) continue;
            Note n = NOTES.get(idx);
            boolean onX = mx >= x + PANEL_W - 22 && mx < x + PANEL_W - 8 && my >= ry + 6 && my < ry + 20;
            boolean onZ = mx >= x + PANEL_W - 38 && mx < x + PANEL_W - 24 && my >= ry + 6 && my < ry + 20;
            if (onZ && Compat.isLeft(button)) {
                if (!TEST_ID.equals(n.clipId)) snooze(n.clipId);
                else NOTES.remove(idx);
                clampScroll(sh);
            } else if (onX || Compat.isRight(button)) {
                NOTES.remove(idx);
                clampScroll(sh);
            } else if (Compat.isLeft(button)) {
                activate(n, chatInput, host);
                NOTES.remove(n);
                clampScroll(sh);
            }
            return true;
        }
        return true; // header / padding: swallow the click so it doesn't hit the chat underneath
    }

    private static void activate(Note n, EditBox chatInput, Screen host) {
        if (TEST_ID.equals(n.clipId)) return;
        Clip c = ClipStore.find(n.clipId);
        if (c == null) return;
        if (c.isSign()) {
            ClipboardScreen list = new ClipboardScreen(host);
            Compat.setScreen(new SignScreen(c, list, host, false));
        } else if (c.isReminder()) {
            ClipboardScreen list = new ClipboardScreen(host);
            Compat.setScreen(new ReminderScreen(c, list, host, false));
        } else if (c.isBook()) {
            ClipboardScreen list = new ClipboardScreen(host);
            Compat.setScreen(new NotebookScreen(c, list, host, false));
        } else if (chatInput != null) {
            ChatState.append(chatInput, Placeholders.expand(c.content));
        }
    }
}
