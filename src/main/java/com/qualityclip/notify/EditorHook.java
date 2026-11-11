package com.qualityclip.notify;

import com.qualityclip.QualityClipMod;
import com.qualityclip.compat.BookBridge;
import com.qualityclip.compat.Reflect;
import com.qualityclip.compat.SignBridge;
import com.qualityclip.compat.Compat;
import com.qualityclip.data.Clip;
import com.qualityclip.data.ClipStore;
import com.qualityclip.screen.ClipboardScreen;
import com.qualityclip.ui.Icons;
import com.qualityclip.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * QualityClip's two buttons on the Book & Quill screen:
 * [open clipboard] and [copy this book as a new clip].
 *
 * They are real vanilla Button widgets added to the screen (so clicks work exactly like the book's own buttons,
 * with or without Scribble) and the icons/tooltips are painted over them by a render hook on the base Screen.
 * They sit in whichever screen corner has free space, so they never collide with Scribble's tools.
 */
public final class EditorHook {
    private static final int S = 20, GAP = 4;
    private static boolean broken = false;
    private static Method addWidget;
    private static final Map<Object, Button[]> BUTTONS = new WeakHashMap<>();

    public static final int NONE = 0, BOOK = 1, SIGN = 2;
    private static final Map<Class<?>, Integer> KINDS = new WeakHashMap<>();
    private static Screen lastSeen;

    public static String status = "";
    public static long statusUntil = 0;

    private EditorHook() {}

    public static void say(String msg) {
        status = msg;
        statusUntil = System.currentTimeMillis() + 5000;
    }

    private static void fail(Throwable t) {
        broken = true;
        QualityClipMod.LOGGER.error("QualityClip book hook disabled after an error", t);
    }

    // ------------------------------------------------------------ attaching

    /**
     * What kind of editor is this screen? Vanilla screens match by class; Scribble 2.x ships its own recreated
     * book screen (not a BookEditScreen subclass), so we also match by class name.
     */
    public static int kind(Object screen) {
        if (screen == null) return NONE;
        Class<?> c = screen.getClass();
        Integer cached = KINDS.get(c);
        if (cached != null) return cached;
        int k = NONE;
        for (Class<?> x = c; x != null && x != Object.class && k == NONE; x = x.getSuperclass()) {
            String n = x.getName().toLowerCase();
            if (n.contains("signedit")) k = SIGN;
            else if (n.contains("bookedit")) k = BOOK;
            else if (n.contains("book") && n.contains("edit") && !n.contains("sign")) k = BOOK;
            else if (n.contains("scribble") && n.contains("book") && !n.contains("view") && !n.contains("sign")
                    && !n.contains("lectern") && !n.contains("widget") && !n.contains("mixin")) k = BOOK;
        }
        KINDS.put(c, k);
        return k;
    }

    /** logs every screen class the first time it is seen if it looks book/sign/Scribble related, to help diagnosis */
    private static void diagnose(Screen s) {
        if (s == lastSeen) return;
        lastSeen = s;
        if (s == null) return;
        String n = s.getClass().getName();
        String low = n.toLowerCase();
        int k = kind(s);
        if (k != NONE) {
            QualityClipMod.LOGGER.info("QualityClip: attaching to " + (k == BOOK ? "book" : "sign") + " screen " + n);
            QualityClipMod.LOGGER.info("QualityClip: editor-debug " + Reflect.describe(s));
        } else if (low.contains("scribble") || low.contains("book") || low.contains("sign")) {
            QualityClipMod.LOGGER.info("QualityClip: saw screen " + n + " (not treated as an editor)");
        }
    }

    /** called every client tick */
    public static void tick() {
        if (broken) return;
        try {
            Screen s = Compat.screen();
            diagnose(s);
            if (kind(s) == NONE) return;
            Button[] have = BUTTONS.get(s);
            if (have != null && present(s, have)) return;
            attach(s);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static boolean present(Screen s, Button[] bs) {
        int found = 0;
        for (GuiEventListener l : s.children()) {
            if (l == bs[0] || l == bs[1]) found++;
        }
        return found == 2;
    }

    private static void attach(Screen s) throws Exception {
        if (addWidget == null) {
            addWidget = Screen.class.getDeclaredMethod("addRenderableWidget", GuiEventListener.class);
            addWidget.setAccessible(true);
        }
        int[] pos = freeSpot(s);
        Button open = Button.builder(Component.literal(""), b -> openClips(s)).bounds(pos[0], pos[1], S, S).build();
        Button copy = Button.builder(Component.literal(""), b -> copyScreen(s)).bounds(pos[0] + S + GAP, pos[1], S, S).build();
        addWidget.invoke(s, open);
        addWidget.invoke(s, copy);
        BUTTONS.put(s, new Button[]{open, copy});
    }

    /** first screen corner whose 44x20 strip doesn't overlap any existing widget */
    private static int[] freeSpot(Screen s) {
        int w = S * 2 + GAP;
        int[][] spots = {{6, 6}, {s.width - w - 6, 6}, {6, s.height - S - 6}, {s.width - w - 6, s.height - S - 6}};
        for (int[] p : spots) {
            boolean free = true;
            for (GuiEventListener l : s.children()) {
                if (l instanceof AbstractWidget a) {
                    int ax = a.getX(), ay = a.getY(), aw = a.getWidth(), ah = a.getHeight();
                    if (p[0] < ax + aw + 4 && p[0] + w + 4 > ax && p[1] < ay + ah + 4 && p[1] + S + 4 > ay) {
                        free = false;
                        break;
                    }
                }
            }
            if (free) return p;
        }
        return spots[0];
    }

    // ------------------------------------------------------------ actions

    private static void openClips(Screen s) {
        ClipboardScreen.selectCategory(kind(s) == SIGN ? ClipboardScreen.Cat.SIGNS : ClipboardScreen.Cat.NOTES);
        Compat.setScreen(new ClipboardScreen(s));
    }

    private static void copyScreen(Screen s) {
        if (kind(s) == SIGN) {
            String[] lines = SignBridge.read(s);
            if (lines == null) {
                say("Couldn't read this sign.");
                return;
            }
            Clip c = new Clip();
            c.type = Clip.SIGN;
            c.name = "Copied Sign";
            c.pages = new ArrayList<>();
            c.pages.add(String.join("\n", lines));
            c.pages.add("");
            ClipStore.add(c);
            say("Saved the sign as a new clip!");
            return;
        }
        boolean[] hand = new boolean[1];
        List<String> pages = BookBridge.read(s, hand);
        if (pages == null) {
            say("Couldn't read this book.");
            return;
        }
        List<String> copy = new ArrayList<>();
        for (String p : pages) copy.add(p.length() > 1023 ? p.substring(0, 1023) : p);
        while (copy.size() > 1 && copy.get(copy.size() - 1).isEmpty()) copy.remove(copy.size() - 1);
        if (copy.isEmpty()) copy.add("");
        Clip c = new Clip();
        c.type = Clip.BOOK;
        c.name = "Copied Book";
        c.pages = copy;
        ClipStore.add(c);
        say("Saved " + copy.size() + (copy.size() == 1 ? " page" : " pages") + " as a new clip!" + (hand[0] ? " (saved version)" : ""));
    }

    // ------------------------------------------------------------ overlay (icons, tooltips, status)

    public static void overlay(Screen screen, GuiGraphicsExtractor g, int mx, int my) {
        if (broken) return;
        try {
            Button[] bs = BUTTONS.get(screen);
            if (bs == null) return;
            Font font = Minecraft.getInstance().font;
            Icons[] icons = {Icons.CLIP, kind(screen) == SIGN ? Icons.SIGN : Icons.BOOK_PLUS};
            String[] tips = {"QualityClip: open your clipboard", kind(screen) == SIGN ? "Copy this sign as a new clip" : "Copy this book as a new clip"};
            for (int i = 0; i < 2; i++) {
                Button b = bs[i];
                icons[i].draw(g, b.getX() + (S - Icons.SIZE) / 2, b.getY() + (S - Icons.SIZE) / 2, 0xFFFFFFFF, Theme.GOLD);
            }
            for (int i = 0; i < 2; i++) {
                Button b = bs[i];
                if (mx >= b.getX() && mx < b.getX() + S && my >= b.getY() && my < b.getY() + S) {
                    Tip.draw(g, font, tips[i], Math.min(mx + 10, screen.width - font.width(tips[i]) - 12),
                            my + 14 > screen.height - 20 ? my - 20 : my + 14, screen.width);
                }
            }
            if (System.currentTimeMillis() < statusUntil && !status.isEmpty()) {
                int x = Math.min(bs[0].getX(), bs[1].getX());
                int y = bs[0].getY() + (bs[0].getY() < screen.height / 2 ? S + 6 : -14);
                int w = font.width(status) + 10;
                x = Math.max(4, Math.min(x, screen.width - w - 4));
                g.fill(x - 1, y - 3, x + w + 1, y + 11, Theme.BORDER);
                g.fill(x, y - 2, x + w, y + 10, 0xF0202830);
                g.text(font, Component.literal(status), x + 5, y, Theme.GOLD);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }
}
