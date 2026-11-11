package com.qualityclip.screen;

import com.qualityclip.QualityClipMod;
import com.qualityclip.compat.Compat;
import com.qualityclip.compat.Safe;
import com.qualityclip.data.Settings;
import com.qualityclip.notify.NotificationManager;
import com.qualityclip.notify.Toasts;
import com.qualityclip.ui.Icons;
import com.qualityclip.ui.Imm;
import com.qualityclip.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

/** Settings, organised into cards: Notifications, Notes, Reminders. Scrolls on small windows. */
public class SettingsScreen extends Screen {
    private final Screen back;
    private final Imm imm = new Imm();
    private int scroll;
    private int contentH;
    private boolean dead;
    private String toast = "";
    private long toastUntil;

    public SettingsScreen(Screen back) {
        super(Component.literal("Settings"));
        this.back = back;
    }

    private void fail(Throwable t) {
        if (dead) return;
        dead = true;
        QualityClipMod.LOGGER.error("QualityClip settings closed after an error", t);
        try {
            Compat.setScreen(back);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onClose() {
        Settings.save();
        Compat.setScreen(back);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        try {
            boolean left = Compat.isLeft(event.button());
            if (left && imm.click((int) event.x(), (int) event.y(), true)) return true;
        } catch (Throwable t) {
            fail(t);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (imm.drag((int) event.x())) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (imm.release()) {
            Settings.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) (scrollY * 18);
        clamp();
        return true;
    }

    private void clamp() {
        int view = this.height - 34;
        scroll = Math.max(0, Math.min(Math.max(0, contentH - view), scroll));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (dead) return;
        try {
            draw(g, mx, my);
            super.extractRenderState(g, mx, my, delta);
            imm.drawTip(g, this.font, this.width);
        } catch (Throwable t) {
            fail(t);
        }
    }

    // ---- row helpers (all positions already include the scroll offset)

    private void card(GuiGraphicsExtractor g, int x, int y, int w, int h, String title, Icons icon) {
        Theme.panel(g, x, y, w, h);
        g.fill(x, y, x + w, y + 18, Theme.PANEL_HI);
        g.fill(x, y + 18, x + w, y + 19, Theme.BORDER);
        icon.draw(g, x + 8, y + 5, Theme.GOLD, Theme.GOLD);
        g.text(this.font, Component.literal(title).withStyle(s -> s.withBold(Boolean.TRUE)), x + 22, y + 5, Theme.TEXT);
    }

    private void switchRow(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String label, String sub,
                           boolean on, Runnable flip) {
        g.text(this.font, Component.literal(Theme.fit(this.font, label, w - 40)), x, y, Theme.TEXT);
        if (sub != null) {
            g.text(this.font, Component.literal(Theme.fit(this.font, sub, w - 40)), x, y + 11, Theme.FAINT);
        }
        imm.toggle(g, mx, my, x + w - 24, y, on, () -> {
            flip.run();
            Settings.save();
        });
    }

    private void draw(GuiGraphicsExtractor g, int mx, int my) {
        int W = this.width, H = this.height;
        Settings s = Settings.get();
        imm.begin();
        g.fill(0, 0, W, H, 0xF2141A20);

        int cw = Math.min(340, W - 20);
        int cx = (W - cw) / 2;
        int inner = cw - 20;

        // fixed header (drawn last so scrolled content slides under it)
        int y = 34 - scroll;
        g.enableScissor(0, 31, W, H);
        try {
            // ---------- Notifications
            int h1 = 236;
            card(g, cx, y, cw, h1, "Notifications", Icons.BELL);
            int ix = cx + 10, iy = y + 26;
            g.text(this.font, Component.literal("Reminder style"), ix, iy, Theme.MUTED);
            imm.seg(g, this.font, mx, my, ix, iy + 11, inner, 18, new String[]{"Standard", "Bold", "SUPER"},
                    s.style.ordinal(), i -> {
                        s.style = Settings.NotifyStyle.values()[i];
                        Settings.save();
                    });
            String desc = switch (s.style) {
                case STANDARD -> "Looks like a vanilla advancement toast.";
                case BOLD -> "Bigger, with a pulsing gold border and a double ping.";
                case SUPER -> "Flashing banner, red screen edges, loud pings. Hard to miss!";
            };
            g.text(this.font, Component.literal(Theme.fit(this.font, desc, inner)), ix, iy + 34, Theme.FAINT);

            int pvY = iy + 47, pvH = 70;
            Theme.inset(g, ix, pvY, inner, pvH);
            g.enableScissor(ix, pvY, ix + inner, pvY + pvH);
            try {
                int w = Math.min(Toasts.width(s.style, inner), inner - 4);
                int h = Toasts.height(s.style);
                Toasts.draw(g, this.font, s.style, ix + (inner - w) / 2, pvY + (pvH - h) / 2, w, "Example Clip", 1,
                        Toasts.TEXT, System.currentTimeMillis());
            } finally {
                g.disableScissor();
            }

            int ry = pvY + pvH + 10;
            g.text(this.font, Component.literal("Stay on screen for"), ix, ry, Theme.TEXT);
            String sec = s.toastSeconds + " seconds";
            g.text(this.font, Component.literal(sec), ix + inner - this.font.width(sec), ry, Theme.GOLD);
            imm.slider(g, this.font, mx, my, ix + 4, ry + 11, inner - 8, s.toastSeconds, 3, 30, 1, v -> s.toastSeconds = v);

            int sy = ry + 34;
            switchRow(g, mx, my, ix, sy, inner, "Play a sound", null, s.sound, () -> s.sound = !s.sound);
            int by = sy + 18;
            imm.btn(g, this.font, mx, my, ix, by, inner, 18, "Send a test reminder", Icons.BELL, Imm.PRIMARY, true,
                    "See your style for real: close this screen, or open chat", () -> {
                        NotificationManager.pushTest();
                        toast = "Test sent! Close this screen to watch it appear.";
                        toastUntil = System.currentTimeMillis() + 3500;
                    });
            y += h1 + 8;

            // ---------- Notes
            int h2 = 74;
            card(g, cx, y, cw, h2, "Notes", Icons.BOOK);
            switchRow(g, mx, my, cx + 10, y + 26, inner, "Two-page view",
                    "Two pages side by side. Needs a wide window.", s.twoPageView, () -> s.twoPageView = !s.twoPageView);
            boolean sc = Safe.scribble();
            Icons.CHECK.draw(g, cx + 10, y + 52, sc ? Theme.GREEN : Theme.FAINT);
            String si = sc ? "Scribble found: colours and formatting are on." : "Scribble not found. Install it for colours and formatting.";
            g.text(this.font, Component.literal(Theme.fit(this.font, si, inner - 14)), cx + 24, y + 53, sc ? Theme.GREEN : Theme.FAINT);
            y += h2 + 8;

            // ---------- Reminders
            int h3 = 88;
            card(g, cx, y, cw, h3, "Reminders", Icons.CLOCK);
            switchRow(g, mx, my, cx + 10, y + 26, inner, "24-hour clock", "Show times like 15:30 instead of 3:30 PM.",
                    s.clock24, () -> s.clock24 = !s.clock24);
            g.text(this.font, Component.literal("Snooze button waits"), cx + 10, y + 54, Theme.TEXT);
            String sn = s.snoozeMinutes + (s.snoozeMinutes == 1 ? " minute" : " minutes");
            g.text(this.font, Component.literal(sn), cx + 10 + inner - this.font.width(sn), y + 54, Theme.GOLD);
            imm.slider(g, this.font, mx, my, cx + 14, y + 65, inner - 8, s.snoozeMinutes, 1, 60, 1, v -> s.snoozeMinutes = v);
            y += h3 + 8;

            // ---------- Quick Clips + data
            int h4 = 78;
            card(g, cx, y, cw, h4, "Quick Clips & data", Icons.BOLT);
            int half = (inner - 4) / 2;
            imm.btn(g, this.font, mx, my, cx + 10, y + 26, half, 18, "Quick Clips...", Icons.BOLT, Imm.PRIMARY, true,
                    "Set up your four Quick Clip slots", () -> Compat.setScreen(new QuickClipsScreen(this)));
            imm.btn(g, this.font, mx, my, cx + 14 + half, y + 26, inner - half - 4, 18, "Data folder", Icons.FOLDER, Imm.NORMAL, true,
                    "Open config/qualityclip (imports and exports live here)", () -> {
                        boolean ok = Safe.openFolder(com.qualityclip.data.Share.root());
                        if (!ok) Safe.copy(com.qualityclip.data.Share.root().toString());
                        toast = ok ? "Opened the data folder." : "Couldn't open it. Path copied instead.";
                        toastUntil = System.currentTimeMillis() + 3500;
                    });
            g.text(this.font, Component.literal(Theme.fit(this.font, "Hotkeys for Quick Clips are in Controls > QualityClip.", inner)), cx + 10, y + 50, Theme.FAINT);
            g.text(this.font, Component.literal(Theme.fit(this.font, "Import: drop .json files on the clipboard screen.", inner)), cx + 10, y + 62, Theme.FAINT);
            y += h4 + 8;

            String about = "QualityClip 0.0.3  -  Minecraft " + Compat.TARGET;
            g.text(this.font, Component.literal(about), (W - this.font.width(about)) / 2, y + 2, Theme.FAINT);
            y += 18;
            contentH = y + scroll - 34;
        } finally {
            g.disableScissor();
        }
        clamp();

        // header bar
        g.fill(0, 0, W, 30, 0xFF0F1419);
        g.fill(0, 30, W, 31, Theme.BORDER);
        imm.iconBtn(g, this.font, mx, my, 6, 5, 20, Icons.BACK, Imm.NORMAL, true, false, "Back", this::onClose);
        g.text(this.font, Component.literal("Settings").withStyle(st -> st.withBold(Boolean.TRUE)), 34, 11, Theme.TEXT);
        if (System.currentTimeMillis() < toastUntil) {
            int tw = this.font.width(toast);
            g.text(this.font, Component.literal(Theme.fit(this.font, toast, W - 120)), W - 10 - Math.min(tw, W - 120), 11, Theme.GREEN);
        }
        if (contentH > H - 34) {
            int view = H - 34;
            int knobH = Math.max(14, view * view / contentH);
            int knobY = 34 + (view - knobH) * scroll / Math.max(1, contentH - view);
            g.fill(W - 4, 34, W - 2, H, 0xFF0F1419);
            g.fill(W - 4, knobY, W - 2, knobY + knobH, 0xFF4A5663);
        }
    }
}
