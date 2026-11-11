package com.qualityclip.screen;

import com.qualityclip.QualityClipMod;
import com.qualityclip.compat.Compat;
import com.qualityclip.compat.Safe;
import com.qualityclip.compat.SignBridge;
import com.qualityclip.data.Clip;
import com.qualityclip.data.ClipStore;
import com.qualityclip.data.Share;
import com.qualityclip.notify.EditorHook;
import com.qualityclip.notify.NotificationManager;
import com.qualityclip.ui.Icons;
import com.qualityclip.ui.Imm;
import com.qualityclip.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Sign editor: four lines on a sign board, front and back, with the same limits as the real sign screen
 * (4 lines, 90 px per line). Pick a wood just for the look. "Paste into sign" replaces the sign you have open.
 */
public class SignScreen extends Screen {
    private static final int MAX_W = 90;
    private static final String[] WOODS = {"Oak", "Spruce", "Birch", "Jungle", "Acacia", "Dark Oak", "Crimson", "Warped"};
    private static final int[] WOOD = {0xFFB8945F, 0xFF7A5A34, 0xFFD8C98D, 0xFFB88764, 0xFFBA6337, 0xFF4C3318, 0xFF7B3250, 0xFF2B7E75};

    private final Clip clip;
    private final Screen back;
    private final Screen host;
    private final boolean isNew;
    private final Imm imm = new Imm();

    private boolean dead;
    private int side;           // 0 = front, 1 = back
    private int line;
    private int col;
    private final String[] lines = new String[4];
    private boolean editing = true;
    private boolean confirmDelete, confirmPaste;
    private String status = "";
    private long statusUntil;
    private boolean statusBad;
    private EditBox nameBox, proxy;

    public SignScreen(Clip clip, Screen back, Screen host, boolean isNew) {
        super(Component.literal("Sign"));
        this.clip = clip;
        this.back = back;
        this.host = host;
        this.isNew = isNew;
        load();
    }

    // ------------------------------------------------------------ data

    private void load() {
        while (clip.pages().size() < 2) clip.pages.add("");
        String[] p = clip.pages().get(side).split("\n", -1);
        for (int i = 0; i < 4; i++) lines[i] = i < p.length ? p[i] : "";
        line = 0;
        col = lines[0].length();
    }

    private void save() {
        clip.pages().set(side, String.join("\n", lines));
        ClipStore.save();
    }

    private int wood() {
        try {
            return Math.max(0, Math.min(WOODS.length - 1, Integer.parseInt(clip.content.trim())));
        } catch (Exception e) {
            return 0;
        }
    }

    private void say(String m, boolean bad) {
        status = m;
        statusBad = bad;
        statusUntil = System.currentTimeMillis() + 4000;
    }

    private void fail(Throwable t) {
        if (dead) return;
        dead = true;
        QualityClipMod.LOGGER.error("QualityClip sign editor closed after an error", t);
        try {
            save();
            Compat.setScreen(back);
        } catch (Throwable ignored) {
        }
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    // ------------------------------------------------------------ geometry

    private int y0() {
        return Math.max(2, (this.height - 150) / 2);
    }

    private int boardW() {
        return 124;
    }

    private int boardX() {
        return (this.width - boardW()) / 2;
    }

    private int boardY() {
        return y0() + 40;
    }

    @Override
    protected void init() {
        try {
            nameBox = addRenderableWidget(new EditBox(this.font, this.width / 2 - 70, y0(), 140, 16, Component.literal("Sign name")));
            nameBox.setMaxLength(32);
            nameBox.setValue(clip.name);
            nameBox.setResponder(v -> {
                clip.name = v;
                ClipStore.save();
            });
            proxy = addRenderableWidget(new EditBox(this.font, -100, -100, 10, 10, Component.literal("")));
            setEditing(editing);
        } catch (Throwable t) {
            fail(t);
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

    // ------------------------------------------------------------ actions

    private void switchSide(int s) {
        save();
        side = s;
        load();
    }

    private String text() {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(l);
        }
        return sb.toString();
    }

    private void pasteIntoSign() {
        if (!confirmPaste) {
            confirmPaste = true;
            say("Warning: this REPLACES all four lines on your sign. Click again to confirm.", true);
            statusUntil = System.currentTimeMillis() + 7000;
            return;
        }
        confirmPaste = false;
        save();
        boolean[] ok = new boolean[1];
        String msg = SignBridge.paste(host, lines.clone(), ok);
        if (ok[0]) {
            EditorHook.say(msg);
            Compat.setScreen(host);
        } else {
            say(msg, true);
        }
    }

    private void exportThis() {
        save();
        java.nio.file.Path f = Share.export(java.util.List.of(clip), clip.name);
        if (f == null) {
            say("Couldn't write the export file.", true);
            return;
        }
        Safe.copy(f.toString());
        say("Exported to " + f.getFileName() + " (path copied)", false);
    }

    private void deleteClip() {
        if (!confirmDelete) {
            confirmDelete = true;
            say("Click the trash again to delete this sign clip.", true);
            return;
        }
        NotificationManager.removeForClip(clip.id);
        ClipStore.remove(clip);
        Compat.setScreen(back);
    }

    private void openSchedule() {
        commit();
        if (back instanceof ClipboardScreen cs) {
            cs.openScheduleFor(clip);
            Compat.setScreen(cs);
        }
    }

    private void commit() {
        save();
        if (isNew && clip.isBlank() && "New Sign".equals(clip.name) && clip.schedule == null) {
            ClipStore.remove(clip);
        } else if (clip.name == null || clip.name.isBlank()) {
            clip.name = "Untitled";
            ClipStore.save();
        }
    }

    @Override
    public void onClose() {
        setEditing(false);
        commit();
        Compat.setScreen(back);
    }

    // ------------------------------------------------------------ editing

    private boolean fits(String s) {
        return this.font.width(s) <= MAX_W;
    }

    private void insert(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\r') continue;
            if (ch == '\n') {
                if (line < 3) {
                    line++;
                    col = Math.min(col, lines[line].length());
                }
                continue;
            }
            if (ch < 32 || ch == 127 || ch == '\u00a7') continue;
            String l = lines[line];
            String nl = l.substring(0, col) + ch + l.substring(col);
            if (!fits(nl)) return;
            lines[line] = nl;
            col++;
        }
        save();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        try {
            if (editing && !dead) {
                int k = event.key(), m = event.modifiers();
                if (k == Compat.K_ESC) return super.keyPressed(event);
                boolean ctrl = Compat.ctrl(m);
                if (ctrl && k == Compat.K_V) {
                    insert(Safe.clipboard());
                    return true;
                }
                if (ctrl && k == Compat.K_C) {
                    Safe.copy(text());
                    say("Copied the sign text!", false);
                    return true;
                }
                if (k == Compat.K_BACKSPACE) {
                    if (col > 0) {
                        lines[line] = lines[line].substring(0, col - 1) + lines[line].substring(col);
                        col--;
                    } else if (line > 0) {
                        line--;
                        col = lines[line].length();
                    }
                    save();
                } else if (k == Compat.K_DELETE) {
                    if (col < lines[line].length()) lines[line] = lines[line].substring(0, col) + lines[line].substring(col + 1);
                    save();
                } else if (k == Compat.K_ENTER || k == Compat.K_KP_ENTER || k == Compat.K_DOWN) {
                    if (line < 3) {
                        line++;
                        col = Math.min(col, lines[line].length());
                    }
                } else if (k == Compat.K_UP) {
                    if (line > 0) {
                        line--;
                        col = Math.min(col, lines[line].length());
                    }
                } else if (k == Compat.K_LEFT) {
                    if (col > 0) col--;
                } else if (k == Compat.K_RIGHT) {
                    if (col < lines[line].length()) col++;
                } else if (k == Compat.K_HOME) {
                    col = 0;
                } else if (k == Compat.K_END) {
                    col = lines[line].length();
                } else {
                    return super.keyPressed(event);
                }
                return true;
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
                int cp = event.codepoint();
                if (cp >= 32 && cp != 127 && cp <= 0xFFFF) insert(String.valueOf((char) cp));
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
                int bx = boardX(), by = boardY();
                if (Imm.over(mx, my, bx, by, boardW(), 56)) {
                    setEditing(true);
                    line = Math.max(0, Math.min(3, (my - (by + 9)) / 10));
                    String l = lines[line];
                    int startX = bx + (boardW() - this.font.width(l)) / 2;
                    int best = l.length(), acc = 0;
                    for (int i = 0; i < l.length(); i++) {
                        int w = this.font.width(String.valueOf(l.charAt(i)));
                        if (mx < startX + acc + w / 2) {
                            best = i;
                            break;
                        }
                        acc += w;
                    }
                    col = best;
                    return true;
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

    // ------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (dead) return;
        try {
            imm.begin();
            g.fill(0, 0, this.width, this.height, 0xE00A0E12);
            drawSign(g, mx, my);
            drawControls(g, mx, my);
            super.extractRenderState(g, mx, my, delta);
            drawStatus(g);
            imm.drawTip(g, this.font, this.width);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void drawSign(GuiGraphicsExtractor g, int mx, int my) {
        int bx = boardX(), by = boardY(), bw = boardW();
        int wood = WOOD[wood()];
        int dark = Theme.mix(wood, 0xFF000000, 0.45);
        int light = Theme.mix(wood, 0xFFFFFFFF, 0.2);
        // post
        g.fill(bx + bw / 2 - 3, by + 56, bx + bw / 2 + 3, by + 84, 0xFF4A3520);
        g.fill(bx + bw / 2 - 3, by + 56, bx + bw / 2 - 2, by + 84, 0xFF5E4529);
        // board
        Theme.rr(g, bx - 2, by - 2, bw + 4, 60, 0xFF000000);
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 57, dark);
        g.fill(bx, by, bx + bw, by + 56, wood);
        g.fill(bx, by, bx + bw, by + 1, light);
        for (int i = 1; i < 6; i++) g.fill(bx, by + i * 9 + 3, bx + bw, by + i * 9 + 4, Theme.mix(wood, 0xFF000000, 0.12));
        if (editing) g.fill(bx - 2, by + 57, bx + bw + 2, by + 58, Theme.GOLD);

        for (int i = 0; i < 4; i++) {
            String l = lines[i];
            int tw = this.font.width(l);
            int tx = bx + (bw - tw) / 2, ty = by + 9 + i * 10;
            g.text(this.font, Component.literal(l), tx, ty, 0xFF000000, false);
            if (editing && i == line && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = tx + this.font.width(l.substring(0, Math.min(col, l.length())));
                g.fill(cx, ty - 1, cx + 1, ty + 9, 0xFF000000);
            }
        }
        // line-width meter for the current line
        int used = Math.min(MAX_W, this.font.width(lines[line]));
        int mxx = bx + (bw - MAX_W) / 2;
        g.fill(mxx, by + 60, mxx + MAX_W, by + 62, 0xFF11171C);
        g.fill(mxx, by + 60, mxx + used, by + 62, used > MAX_W - 8 ? Theme.RED : Theme.TEAL);
    }

    private void drawControls(GuiGraphicsExtractor g, int mx, int my) {
        int mid = this.width / 2, y0 = y0();
        imm.iconBtn(g, this.font, mx, my, mid - 70 - 24, y0 - 2, 20, Icons.BACK, Imm.NORMAL, true, false, "Back (saves automatically)", this::onClose);
        imm.iconBtn(g, this.font, mx, my, mid + 70 + 4, y0 - 2, 20, Icons.BELL, clip.schedule != null ? Imm.PRIMARY : Imm.NORMAL,
                true, false, "Click to set schedule", this::openSchedule);

        // front / back
        imm.seg(g, this.font, mx, my, boardX(), y0 + 20, boardW(), 14, new String[]{"Front", "Back"}, side, this::switchSide);

        // wood chips under the post
        int cy = boardY() + 90;
        int cw = 12, gap = 3;
        int total = WOODS.length * cw + (WOODS.length - 1) * gap;
        int cx = (this.width - total) / 2;
        for (int i = 0; i < WOODS.length; i++) {
            int x = cx + i * (cw + gap);
            boolean hover = Imm.over(mx, my, x, cy, cw, cw);
            g.fill(x - 1, cy - 1, x + cw + 1, cy + cw + 1, i == wood() ? Theme.GOLD : hover ? 0xFFFFFFFF : Theme.BORDER);
            g.fill(x, cy, x + cw, cy + cw, WOOD[i]);
            final int idx = i;
            imm.area(mx, my, x, cy, cw, cw, () -> {
                clip.content = String.valueOf(idx);
                ClipStore.save();
            }, null);
            if (hover) com.qualityclip.notify.Tip.draw(g, this.font, WOODS[i] + " (just for the look)", mx + 8, my - 18, this.width);
        }

        // action icons on the right
        int rx = Math.min(boardX() + boardW() + 14, this.width - 46);
        int ry = boardY() - 4;
        int n = 0;
        n = icon(g, mx, my, rx, ry, n, Icons.COPY, Imm.NORMAL, false, "Copy the sign text", () -> {
            Safe.copy(text());
            say("Copied the sign text!", false);
        });
        n = icon(g, mx, my, rx, ry, n, Icons.EXPORT, Imm.NORMAL, false, "Save this sign as a .json file", this::exportThis);
        n = icon(g, mx, my, rx, ry, n, Icons.TRASH, confirmDelete ? Imm.DANGER : Imm.NORMAL, confirmDelete,
                confirmDelete ? "Click again to delete this sign clip" : "Delete this sign clip", this::deleteClip);
        if (EditorHook.kind(host) == EditorHook.SIGN) {
            icon(g, mx, my, rx, ry, n, Icons.PASTE_IN, confirmPaste ? Imm.DANGER : Imm.PRIMARY, confirmPaste,
                    confirmPaste ? "Click again: REPLACES all four lines" : "Paste into your open sign (replaces it)", this::pasteIntoSign);
        }
    }

    private int icon(GuiGraphicsExtractor g, int mx, int my, int rx, int ry, int n, Icons ic, int style, boolean sel, String tip, Runnable act) {
        imm.iconBtn(g, this.font, mx, my, rx + (n % 2) * 24, ry + (n / 2) * 24, 20, ic, style, true, sel, tip, act);
        return n + 1;
    }

    private void drawStatus(GuiGraphicsExtractor g) {
        if (System.currentTimeMillis() < statusUntil && !status.isEmpty()) {
            String s = Theme.fit(this.font, status, this.width - 16);
            int w = this.font.width(s) + 12;
            int x = (this.width - w) / 2;
            int y = Math.min(this.height - 16, boardY() + 108);
            g.fill(x - 1, y - 1, x + w + 1, y + 13, Theme.BORDER);
            g.fill(x, y, x + w, y + 12, 0xF0202830);
            g.text(this.font, Component.literal(s), x + 6, y + 2, statusBad ? Theme.RED : Theme.GREEN);
        }
    }
}
