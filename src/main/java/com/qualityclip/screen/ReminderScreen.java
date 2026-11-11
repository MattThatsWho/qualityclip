package com.qualityclip.screen;

import com.qualityclip.QualityClipMod;
import com.qualityclip.compat.Compat;
import com.qualityclip.compat.Safe;
import com.qualityclip.data.Clip;
import com.qualityclip.data.ClipStore;
import com.qualityclip.data.Schedule;
import com.qualityclip.data.Settings;
import com.qualityclip.data.Share;
import com.qualityclip.notify.NotificationManager;
import com.qualityclip.ui.Icons;
import com.qualityclip.ui.Imm;
import com.qualityclip.ui.TextArea;
import com.qualityclip.ui.Theme;
import com.qualityclip.ui.Time;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Reminder editor: a name (shown in the notification), optional details (as much text as you like), and a time,
 * either "in a while" (minutes / hours / days from now) or "at a time" (a day + clock time).
 */
public class ReminderScreen extends Screen {
    private static final int MAX_DETAILS = 1000;

    private final Clip clip;
    private final Screen back;
    private final Screen host;
    private final boolean isNew;
    private final Imm imm = new Imm();
    private boolean dead;

    private EditBox nameBox;
    private EditBox proxy; // invisible: keeps the game's text-input mode on while typing in the details box
    private final TextArea details = new TextArea(120, MAX_DETAILS, 0);
    private boolean editingDetails;
    private int detailsX, detailsY, detailsW, detailsH, detailsLines;

    private int whenMode;        // 0 = in a while, 1 = at a time
    private int inAmount = 30;
    private int inUnit;          // 0 min, 1 h, 2 days, 3 Minecraft days (20 real minutes each)
    private int repeat;          // 0 never, 1 daily, 2 weekly
    private boolean dragDetails;
    private int atDay;
    private int atHour = 12;
    private int atMinute;
    private String message = "";
    private boolean messageBad;

    public ReminderScreen(Clip clip, Screen back, Screen host, boolean isNew) {
        super(Component.literal("Reminder"));
        this.clip = clip;
        this.back = back;
        this.host = host;
        this.isNew = isNew;
        details.set(clip.content);
        repeat = "WEEKLY".equals(clip.repeat) ? 2 : "DAILY".equals(clip.repeat) ? 1 : 0;
        if (clip.schedule != null) {
            whenMode = 1;
            java.time.LocalDateTime t = Time.local(clip.schedule.nextFireMs);
            long days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), t.toLocalDate());
            atDay = (int) Math.max(0, Math.min(30, days));
            atHour = t.getHour();
            atMinute = t.getMinute();
        } else {
            java.time.LocalDateTime t = java.time.LocalDateTime.now().plusHours(1);
            atHour = t.getHour();
            atMinute = 0;
        }
    }

    private void fail(Throwable t) {
        if (dead) return;
        dead = true;
        QualityClipMod.LOGGER.error("QualityClip reminder editor closed after an error", t);
        try {
            ClipStore.save();
            Compat.setScreen(back);
        } catch (Throwable ignored) {
        }
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    // ------------------------------------------------------------ geometry

    private int pw() {
        return Math.min(330, this.width - 10);
    }

    private int ph() {
        return Math.min(214, this.height - 6);
    }

    private int px() {
        return (this.width - pw()) / 2;
    }

    private int py() {
        return Math.max(3, (this.height - ph()) / 2);
    }

    private boolean twoCol() {
        return pw() >= 300;
    }

    private int leftW() {
        return twoCol() ? (pw() - 30) / 2 : pw() - 20;
    }

    @Override
    protected void init() {
        try {
            int px = px(), py = py();
            nameBox = addRenderableWidget(new EditBox(this.font, px + 10, py + 38, leftW(), 16, Component.literal("Reminder name")));
            nameBox.setMaxLength(32);
            nameBox.setValue(clip.name);
            nameBox.setResponder(v -> {
                clip.name = v;
                ClipStore.save();
            });
            proxy = addRenderableWidget(new EditBox(this.font, -100, -100, 10, 10, Component.literal("")));
        } catch (Throwable t) {
            fail(t);
        }
    }

    // ------------------------------------------------------------ logic

    private long computeDue() {
        if (whenMode == 0) return Time.inFromNow(inAmount, inUnit);
        return Time.at(atDay, atHour, atMinute);
    }

    private void setReminder() {
        long due = computeDue();
        if (due <= System.currentTimeMillis() + 5_000L) {
            message = "That time has already passed.";
            messageBad = true;
            return;
        }
        Schedule s = new Schedule();
        s.mode = Schedule.Mode.ONCE;
        s.sound = true;
        s.nextFireMs = due;
        clip.schedule = s;
        clip.repeat = repeat == 2 ? "WEEKLY" : repeat == 1 ? "DAILY" : "NONE";
        clip.doneMs = 0;
        NotificationManager.removeForClip(clip.id);
        ClipStore.save();
        message = "Reminder set!";
        messageBad = false;
    }

    private void clearReminder() {
        clip.schedule = null;
        clip.doneMs = 0;
        NotificationManager.removeForClip(clip.id);
        ClipStore.save();
        message = "Cleared.";
        messageBad = false;
    }

    private void commit() {
        clip.content = details.text;
        if (clip.name == null || clip.name.isBlank()) clip.name = "Untitled";
        if (isNew && clip.isBlank() && "New Reminder".equals(clip.name) && clip.schedule == null) {
            ClipStore.remove(clip);
        } else {
            ClipStore.save();
        }
    }

    private void exportThis() {
        clip.content = details.text;
        java.nio.file.Path f = Share.export(java.util.List.of(clip), clip.name);
        if (f == null) {
            message = "Couldn't write the export file.";
            messageBad = true;
            return;
        }
        Safe.copy(f.toString());
        message = "Exported! Path copied.";
        messageBad = false;
    }

    private void deleteReminder() {
        NotificationManager.removeForClip(clip.id);
        ClipStore.remove(clip);
        Compat.setScreen(back);
    }

    @Override
    public void onClose() {
        setDetailsFocus(false);
        commit();
        Compat.setScreen(back);
    }

    private void setDetailsFocus(boolean on) {
        editingDetails = on;
        try {
            if (proxy != null) proxy.setFocused(on);
        } catch (Throwable ignored) {
        }
    }

    /** read by the game (26.3+) to know a custom text box is taking keystrokes */
    public boolean isInputCaptured() {
        return editingDetails;
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        try {
            int mx = (int) event.x(), my = (int) event.y();
            boolean left = Compat.isLeft(event.button());
            if (left && Imm.over(mx, my, detailsX, detailsY, detailsW, detailsH)) {
                setDetailsFocus(true);
                dragDetails = true;
                details.press(this.font, mx - detailsX - 4, my - detailsY - 3, false, detailsLines);
                return true;
            }
            if (left && imm.click(mx, my, true)) {
                return true;
            }
            boolean r = super.mouseClicked(event, doubleClick);
            if (r) setDetailsFocus(false);
            return r;
        } catch (Throwable t) {
            fail(t);
            return true;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragDetails && editingDetails) {
            details.drag(this.font, (int) event.x() - detailsX - 4, (int) event.y() - detailsY - 3, detailsLines);
            return true;
        }
        if (imm.drag((int) event.x())) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragDetails) {
            dragDetails = false;
            return true;
        }
        if (imm.release()) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (Imm.over((int) mx, (int) my, detailsX, detailsY, detailsW, detailsH)) {
            details.scroll(this.font, -(int) Math.signum(sy), detailsLines);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        try {
            if (editingDetails && !dead) {
                int k = event.key();
                if (k != Compat.K_ESC) {
                    if (details.key(this.font, k, event.modifiers(), false)) {
                        details.ensureVisible(this.font, detailsLines);
                        clip.content = details.text;
                        ClipStore.save();
                        return true;
                    }
                    if (Compat.ctrl(event.modifiers()) && k == Compat.K_C) {
                        Safe.copy(details.text);
                        return true;
                    }
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
            if (editingDetails && !dead) {
                details.typed(this.font, event.codepoint(), false);
                details.ensureVisible(this.font, detailsLines);
                clip.content = details.text;
                ClipStore.save();
                return true;
            }
        } catch (Throwable t) {
            fail(t);
            return true;
        }
        return super.charTyped(event);
    }

    // ------------------------------------------------------------ drawing

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

    private void draw(GuiGraphicsExtractor g, int mx, int my) {
        imm.begin();
        int px = px(), py = py(), pw = pw(), ph = ph();
        boolean h24 = Settings.get().clock24;
        g.fill(0, 0, this.width, this.height, 0xE00A0E12);
        Theme.panel(g, px, py, pw, ph);

        // header
        g.fill(px, py, px + pw, py + 24, Theme.PANEL_HI);
        g.fill(px, py + 24, px + pw, py + 25, Theme.BORDER);
        imm.iconBtn(g, this.font, mx, my, px + 4, py + 2, 20, Icons.BACK, Imm.GHOST, true, false, "Back (saves automatically)", this::onClose);
        Icons.BELL.draw(g, px + 30, py + 8, Theme.BLUE, Theme.BLUE);
        g.text(this.font, Component.literal("Reminder").withStyle(s -> s.withBold(Boolean.TRUE)), px + 43, py + 8, Theme.TEXT);
        imm.iconBtn(g, this.font, mx, my, px + pw - 24, py + 2, 20, Icons.TRASH, Imm.DANGER, true, false, "Delete this reminder", this::deleteReminder);
        imm.iconBtn(g, this.font, mx, my, px + pw - 48, py + 2, 20, Icons.EXPORT, Imm.GHOST, true, false, "Save this reminder as a .json file", this::exportThis);

        int lx = px + 10, lw = leftW();
        int rx = twoCol() ? lx + lw + 10 : lx;
        int rw = twoCol() ? lw : lw;
        int botY = py + ph - 26;

        // ----- left: name + details
        g.text(this.font, Component.literal(Theme.fit(this.font, "Alert name", lw)), lx, py + 29, Theme.MUTED);
        int dy = py + 60;
        String cnt = details.text.length() + "/" + MAX_DETAILS;
        g.text(this.font, Component.literal(cnt), lx + lw - this.font.width(cnt), dy, Theme.FAINT);
        g.text(this.font, Component.literal(Theme.fit(this.font, "Details", lw - this.font.width(cnt) - 6)), lx, dy, Theme.MUTED);
        detailsX = lx;
        detailsY = dy + 10;
        detailsW = lw;
        int bottomLimit = twoCol() ? botY - 8 : py + ph - 120;
        int repeatSegY = bottomLimit - 16;
        detailsH = Math.max(24, repeatSegY - 12 - 4 - detailsY);
        detailsLines = Math.max(1, (detailsH - 6) / 9);
        details.setWidth(detailsW - 10);
        Theme.inset(g, detailsX, detailsY, detailsW, detailsH);
        if (editingDetails) g.fill(detailsX - 1, detailsY - 1, detailsX + detailsW + 1, detailsY, Theme.GOLD);
        if (details.text.isEmpty() && !editingDetails) {
            g.text(this.font, Component.literal("Click to add notes..."), detailsX + 5, detailsY + 4, Theme.FAINT);
        }
        g.enableScissor(detailsX, detailsY, detailsX + detailsW, detailsY + detailsH);
        try {
            details.draw(g, this.font, detailsX + 4, detailsY + 4, detailsLines, editingDetails, Theme.TEXT);
        } finally {
            g.disableScissor();
        }

        g.text(this.font, Component.literal("Repeat"), lx, repeatSegY - 11, Theme.MUTED);
        imm.seg(g, this.font, mx, my, lx, repeatSegY, lw, 16, new String[]{"Never", "Daily", "Weekly"}, repeat, i -> {
            repeat = i;
            message = "";
        });

        // ----- right: when
        int ry = twoCol() ? py + 29 : py + 29 + 0;
        if (!twoCol()) ry = bottomLimit + 4;
        g.text(this.font, Component.literal("When"), rx, ry, Theme.MUTED);
        imm.seg(g, this.font, mx, my, rx, ry + 10, rw, 18, new String[]{"In a while", "At a time"}, whenMode, i -> {
            whenMode = i;
            message = "";
        });
        int cy = ry + 36;
        if (whenMode == 0) {
            int max = inUnit == 0 ? 60 : inUnit == 1 ? 48 : 30;
            if (inAmount > max) inAmount = max;
            String[] units = {"minute", "hour", "day", "MC day"};
            String val = inAmount + " " + units[inUnit] + (inAmount == 1 ? "" : "s");
            g.text(this.font, Component.literal("Remind me in"), rx, cy, Theme.TEXT);
            g.text(this.font, Component.literal(val), rx + rw - this.font.width(val), cy, Theme.GOLD);
            imm.slider(g, this.font, mx, my, rx + 4, cy + 10, rw - 8, inAmount, 1, max, 1, v -> {
                inAmount = v;
                message = "";
            });
            imm.seg(g, this.font, mx, my, rx, cy + 30, rw, 16, new String[]{"Min", "Hours", "Days", "MC days"}, inUnit, i -> {
                inUnit = i;
                int m = i == 0 ? 60 : i == 1 ? 48 : 30;
                if (inAmount > m) inAmount = m;
                message = "";
            });
            cy += 52;
        } else {
            g.text(this.font, Component.literal("Day"), rx, cy, Theme.TEXT);
            String dl = Time.dateLabel(atDay);
            g.text(this.font, Component.literal(dl), rx + rw - this.font.width(dl), cy, Theme.GOLD);
            imm.slider(g, this.font, mx, my, rx + 4, cy + 10, rw - 8, atDay, 0, 30, 1, v -> {
                atDay = v;
                message = "";
            });
            g.text(this.font, Component.literal("Time"), rx, cy + 30, Theme.TEXT);
            String tl = Time.clock(atHour, atMinute, h24);
            g.text(this.font, Component.literal(tl), rx + rw - this.font.width(tl), cy + 30, Theme.GOLD);
            int hw = (rw - 8) / 2;
            imm.slider(g, this.font, mx, my, rx + 4, cy + 40, hw - 4, atHour, 0, 23, 1, v -> {
                atHour = v;
                message = "";
            });
            imm.slider(g, this.font, mx, my, rx + 4 + hw + 4, cy + 40, hw - 4, atMinute, 0, 59, 1, v -> {
                atMinute = v;
                message = "";
            });
            cy += 62;
        }

        // result line
        long due = computeDue();
        boolean past = due <= System.currentTimeMillis() + 5_000L;
        String r1, r2;
        int rc;
        if (!message.isEmpty()) {
            r1 = message;
            r2 = clip.schedule != null ? "Due " + Time.when(clip.schedule.nextFireMs, h24) : "";
            rc = messageBad ? Theme.RED : Theme.GREEN;
        } else if (past) {
            r1 = "That time has already passed.";
            r2 = "";
            rc = Theme.RED;
        } else {
            r1 = "Due " + Time.relative(due);
            r2 = Time.when(due, h24);
            rc = Theme.GREEN;
        }
        if (cy < botY - 4) {
            g.text(this.font, Component.literal(Theme.fit(this.font, r1, rw)), rx, cy, rc);
            if (!r2.isEmpty() && cy + 10 < botY - 4) g.text(this.font, Component.literal(Theme.fit(this.font, r2, rw)), rx, cy + 10, Theme.MUTED);
        }

        // buttons
        boolean active = clip.schedule != null;
        int inner = pw - 20;
        int w3 = (inner - 8) / 3;
        imm.btn(g, this.font, mx, my, px + 10, botY, w3, 20, active ? "Update" : "Set reminder", Icons.CHECK, Imm.PRIMARY, !past, null, this::setReminder);
        imm.btn(g, this.font, mx, my, px + 14 + w3, botY, w3, 20, "Clear", Icons.CROSS, Imm.DANGER, active || clip.doneMs > 0, null, this::clearReminder);
        imm.btn(g, this.font, mx, my, px + 18 + 2 * w3, botY, inner - 8 - 2 * w3, 20, "Done", Icons.BACK, Imm.NORMAL, true, null, this::onClose);
    }
}
