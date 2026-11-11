package com.qualityclip.notify;

import com.qualityclip.QualityClipMod;
import com.qualityclip.screen.ClipboardScreen;
import com.qualityclip.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * All the chat-screen logic. The mixin only forwards to this class, and every entry point is wrapped
 * so that if anything is ever wrong we log once and switch ourselves off instead of crashing.
 */
public final class ChatHook {
    private static final int BS = 20;
    private static final Map<Object, Object> LAST_INPUT = new WeakHashMap<>();
    private static boolean broken = false;
    private static boolean scrollBroken = false;

    private ChatHook() {}

    private static int bx(Screen s) {
        return s.width - BS - 2; // right side, just above the chat text box
    }

    private static int by(Screen s) {
        return s.height - 36;
    }

    private static EditBox findInput(Screen s) {
        for (GuiEventListener l : s.children()) {
            if (l instanceof EditBox e) return e;
        }
        return null;
    }

    private static void fail(Throwable t) {
        broken = true;
        QualityClipMod.LOGGER.error("QualityClip chat hook disabled after an error", t);
    }

    private static void drawChatButton(GuiGraphicsExtractor g, int x, int y, boolean hover) {
        g.fill(x, y, x + BS, y + BS, hover ? 0xFFFFFFFF : 0xFF000000);
        g.fill(x + 1, y + 1, x + BS - 1, y + BS - 1, hover ? 0xFF8B8B8B : 0xFF707070);
        g.fill(x + 1, y + 1, x + BS - 1, y + 2, 0xFFB5B5B5);
        g.fill(x + 1, y + 1, x + 2, y + BS - 1, 0xFFB5B5B5);
        g.fill(x + 1, y + BS - 2, x + BS - 1, y + BS - 1, 0xFF3A3A3A);
        g.fill(x + BS - 2, y + 1, x + BS - 1, y + BS - 1, 0xFF3A3A3A);
        ClipIcon.draw(g, x + 4, y + 3);
    }

    public static void render(Screen screen, GuiGraphicsExtractor g, int mx, int my) {
        if (broken) return;
        try {
            Font font = Minecraft.getInstance().font;

            EditBox input = findInput(screen);
            if (input != null && LAST_INPUT.get(screen) != input) {
                LAST_INPUT.put(screen, input);
                ChatState.applyOnInit(input); // restore typed text / paste a clip sent from the clipboard
            }

            int x = bx(screen), y = by(screen);
            boolean hover = mx >= x && mx < x + BS && my >= y && my < y + BS;
            drawChatButton(g, x, y, hover);

            NotificationManager.renderPanel(g, font, screen.width, screen.height, mx, my);

            if (hover) {
                Tip.draw(g, font, "Open your QualityClip clipboard", x - 160, my - 20, screen.width);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** @return true when the click was handled (and vanilla should ignore it) */
    public static boolean click(Screen screen, MouseButtonEvent e) {
        if (broken) return false;
        try {
            double mx = e.x(), my = e.y();
            EditBox input = findInput(screen);
            int x = bx(screen), y = by(screen);
            if (Compat.isLeft(e.button()) && mx >= x && mx < x + BS && my >= y && my < y + BS) {
                ChatState.saved = input != null ? input.getValue() : null;
                Compat.setScreen(new ClipboardScreen(screen));
                return true;
            }
            return NotificationManager.mouseClicked(input, screen, screen.width, screen.height, mx, my, e.button());
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    public static boolean scroll(Screen screen, double mx, double my, double dy) {
        if (broken || scrollBroken) return false;
        try {
            return NotificationManager.mouseScrolled(screen.width, screen.height, mx, my, dy);
        } catch (Throwable t) {
            scrollBroken = true;
            QualityClipMod.LOGGER.error("QualityClip panel scrolling disabled after an error", t);
            return false;
        }
    }
}
