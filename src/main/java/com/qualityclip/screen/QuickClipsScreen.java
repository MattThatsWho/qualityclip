package com.qualityclip.screen;

import com.qualityclip.QualityClipMod;
import com.qualityclip.compat.Compat;
import com.qualityclip.data.QuickSlot;
import com.qualityclip.data.Settings;
import com.qualityclip.notify.Placeholders;
import com.qualityclip.notify.QuickClips;
import com.qualityclip.ui.Icons;
import com.qualityclip.ui.Imm;
import com.qualityclip.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick Clips: four slots, each a template with placeholders. Bind a key to each slot in Controls
 * (QualityClip > Quick Clip 1-4); pressing it saves a clip and/or copies the filled-in text.
 */
public class QuickClipsScreen extends Screen {
    private static final String[] CHIPS = {"{x}", "{y}", "{z}", "{pos}", "{biome}", "{dimension}", "{time}", "{date}", "{mctime}", "{day}", "{player}"};
    private static int slot = 0;

    private final Screen back;
    private final Imm imm = new Imm();
    private EditBox nameBox, templateBox;
    private boolean dead;
    private String message = "";
    private long messageUntil;

    public QuickClipsScreen(Screen back) {
        super(Component.literal("Quick Clips"));
        this.back = back;
    }

    private QuickSlot cur() {
        return Settings.get().quick.get(slot);
    }

    private void fail(Throwable t) {
        if (dead) return;
        dead = true;
        QualityClipMod.LOGGER.error("QualityClip quick clips closed after an error", t);
        try {
            Compat.setScreen(back);
        } catch (Throwable ignored) {
        }
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    private boolean twoCol() {
        return this.width >= 300;
    }

    private int px() {
        return Math.max(4, (this.width - Math.min(this.width - 8, 420)) / 2);
    }

    private int pw() {
        return Math.min(this.width - 8, 420);
    }

    private int leftW() {
        return twoCol() ? (pw() - 30) * 11 / 20 : pw() - 20;
    }

    @Override
    protected void init() {
        try {
            int x = px() + 10;
            int y = 58;
            nameBox = addRenderableWidget(new EditBox(this.font, x, y + 10, leftW(), 16, Component.literal("Name")));
            nameBox.setMaxLength(24);
            nameBox.setValue(cur().name);
            nameBox.setResponder(v -> {
                cur().name = v;
                Settings.save();
            });
            templateBox = addRenderableWidget(new EditBox(this.font, x, y + 42, leftW(), 16, Component.literal("Template")));
            templateBox.setMaxLength(200);
            templateBox.setValue(cur().template);
            templateBox.setResponder(v -> {
                cur().template = v;
                Settings.save();
            });
        } catch (Throwable t) {
            fail(t);
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
            if (Compat.isLeft(event.button()) && imm.click((int) event.x(), (int) event.y(), true)) return true;
        } catch (Throwable t) {
            fail(t);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
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

    private void draw(GuiGraphicsExtractor g, int mx, int my) {
        imm.begin();
        int W = this.width, H = this.height, px = px(), pw = pw();
        g.fill(0, 0, W, H, 0xF2141A20);
        g.fill(0, 0, W, 30, 0xFF0F1419);
        g.fill(0, 30, W, 31, Theme.BORDER);
        imm.iconBtn(g, this.font, mx, my, 6, 5, 20, Icons.BACK, Imm.NORMAL, true, false, "Back", this::onClose);
        Icons.BOLT.draw(g, 34, 11, Theme.GOLD, Theme.GOLD);
        g.text(this.font, Component.literal("Quick Clips").withStyle(st -> st.withBold(Boolean.TRUE)), 48, 11, Theme.TEXT);
        String hint = "Set the keys in Controls > QualityClip";
        if (W > 330) g.text(this.font, Component.literal(Theme.fit(this.font, hint, W - 150)), W - 10 - Math.min(this.font.width(hint), W - 150), 11, Theme.FAINT);

        // slot tabs
        String[] labels = new String[QuickSlot.COUNT];
        for (int i = 0; i < labels.length; i++) labels[i] = (i + 1) + ". " + Theme.fit(this.font, Settings.get().quick.get(i).name, Math.max(18, pw / 4 - 28));
        imm.seg(g, this.font, mx, my, px + 10, 38, pw - 20, 16, labels, slot, i -> {
            slot = i;
            message = "";
            rebuild();
        });

        int x = px + 10, y = 58, lw = leftW();
        g.text(this.font, Component.literal("Name"), x, y, Theme.MUTED);
        g.text(this.font, Component.literal(Theme.fit(this.font, "Text to grab", lw)), x, y + 32, Theme.MUTED);
        int my2 = y + 64;
        g.text(this.font, Component.literal(Theme.fit(this.font, "When the key is pressed", lw)), x, my2, Theme.MUTED);
        imm.seg(g, this.font, mx, my, x, my2 + 10, lw, 16, new String[]{"Save clip", "Copy", "Both"}, cur().mode, i -> {
            cur().mode = i;
            Settings.save();
        });

        // right column: placeholder chips, then a live preview just above the buttons
        int rx = twoCol() ? x + lw + 10 : x;
        int rw = twoCol() ? pw - 20 - lw - 10 : lw;
        int ry = twoCol() ? y : my2 + 34;
        int pvH = 30;
        int pvY = H - 34 - pvH;
        if (!twoCol()) pvY = H - 34 - 14;
        g.text(this.font, Component.literal("Click to add"), rx, ry, Theme.MUTED);
        int cols = Math.max(2, rw / 56);
        int cw = (rw - (cols - 1) * 3) / cols;
        int rows = Math.max(1, (pvY - 6 - (ry + 10)) / 13);
        int shown = Math.min(CHIPS.length, rows * cols);
        for (int i = 0; i < shown; i++) {
            int cx = rx + (i % cols) * (cw + 3), cy = ry + 10 + (i / cols) * 13;
            final String chip = CHIPS[i];
            imm.btn(g, this.font, mx, my, cx, cy, cw, 12, chip, null, Imm.GHOST, true, null, () -> {
                String v = templateBox.getValue();
                if (v.length() + chip.length() <= 200) templateBox.setValue(v + chip);
            });
        }
        if (shown < CHIPS.length) {
            g.text(this.font, Component.literal("more: " + Theme.fit(this.font, "{dimension} {mctime} {day} {player}", Math.max(20, rw - 36))),
                    rx, Math.min(pvY - 10, ry + 10 + rows * 13 + 1), Theme.FAINT);
        }

        String pv = Placeholders.expand(cur().template);
        if (twoCol()) {
            Theme.inset(g, rx, pvY, rw, pvH);
            g.text(this.font, Component.literal("Preview"), rx + 4, pvY + 3, Theme.TEAL);
            String body = pv.isEmpty() ? "(empty)" : pv;
            String l1 = Theme.fit(this.font, body, rw - 8);
            g.text(this.font, Component.literal(l1), rx + 4, pvY + 14, pv.isEmpty() ? Theme.FAINT : Theme.TEXT);
        } else {
            Theme.inset(g, px + 10, pvY, pw - 20, 14);
            g.text(this.font, Component.literal(Theme.fit(this.font, pv.isEmpty() ? "(empty)" : "Preview: " + pv, pw - 30)), px + 15, pvY + 3,
                    pv.isEmpty() ? Theme.FAINT : Theme.TEAL);
        }
        int bw = (pw - 20 - 8) / 3;
        imm.btn(g, this.font, mx, my, px + 10, H - 30, bw, 20, "Run now", Icons.BOLT, Imm.PRIMARY, true,
                "Do it right now, as if you pressed the key", () -> {
                    message = QuickClips.run(slot);
                    messageUntil = System.currentTimeMillis() + 4000;
                });
        imm.btn(g, this.font, mx, my, px + 14 + bw, H - 30, bw, 20, "Reset slot", Icons.CROSS, Imm.DANGER, true, null, () -> {
            Settings.get().quick.set(slot, QuickSlot.defaults().get(slot));
            Settings.save();
            rebuild();
        });
        imm.btn(g, this.font, mx, my, px + 18 + 2 * bw, H - 30, pw - 20 - 8 - 2 * bw, 20, "Done", Icons.CHECK, Imm.NORMAL, true, null, this::onClose);
        if (System.currentTimeMillis() < messageUntil && !message.isEmpty()) {
            g.text(this.font, Component.literal(Theme.fit(this.font, message, pw - 20)), px + 10, H - 64, Theme.GREEN);
        }
    }
}
