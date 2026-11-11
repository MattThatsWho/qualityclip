package com.qualityclip.compat;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads pages from, and replaces the pages of, an open Book & Quill screen. Works with the vanilla screen and
 * tries hard with others (Scribble 2.x ships its own screen). Everything is checked BEFORE anything is changed.
 */
public final class BookBridge {
    public static final int MAX_PAGES = 100;

    private BookBridge() {}

    // ------------------------------------------------------------------ reading

    /** pages in the open screen, falling back to the saved copy in your hand. Never null. */
    public static List<String> read(Object screen, boolean[] fromHand) {
        if (fromHand != null) fromHand[0] = false;
        List<String> p = readScreen(screen);
        if (p != null && !p.isEmpty()) return p;
        List<String> h = readHeldItem();
        if (h != null && !h.isEmpty()) {
            if (fromHand != null) fromHand[0] = true;
            return h;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> readScreen(Object screen) {
        try {
            Field f = Reflect.field(screen.getClass(), "pages");
            Object raw = f == null ? null : f.get(screen);
            List<String> out = convert(raw);
            if (out != null) return out;
            // otherwise look at every list on the screen for one that holds page-like things
            List<String> best = null;
            for (Field fl : Reflect.allFields(screen.getClass())) {
                if (!List.class.isAssignableFrom(fl.getType())) continue;
                List<String> c = convert(fl.get(screen));
                if (c != null && !c.isEmpty() && (best == null || c.size() > best.size())) best = c;
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<String> convert(Object raw) {
        if (!(raw instanceof List<?> l)) return null;
        List<String> out = new ArrayList<>();
        for (Object o : l) {
            String s = Reflect.textOf(o);
            if (s == null) return null;
            out.add(s);
        }
        return out;
    }

    /** the saved pages of the writable book you're holding (unsaved edits are not included) */
    public static List<String> readHeldItem() {
        try {
            Minecraft mc = Minecraft.getInstance();
            Object player = Reflect.get(mc, "player");
            if (player == null) return null;
            Class<?> dc = Class.forName("net.minecraft.core.component.DataComponents");
            Object type = dc.getField("WRITABLE_BOOK_CONTENT").get(null);
            for (String hand : new String[]{"getMainHandItem", "getOffhandItem"}) {
                Object stack = player.getClass().getMethod(hand).invoke(player);
                Method get = null;
                for (Method m : stack.getClass().getMethods()) {
                    if (m.getName().equals("get") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(type)) get = m;
                }
                if (get == null) continue;
                Object content = get.invoke(stack, type);
                if (content == null) continue;
                Object pages = content.getClass().getMethod("pages").invoke(content);
                if (!(pages instanceof List<?> l)) continue;
                List<String> out = new ArrayList<>();
                for (Object o : l) {
                    String s = Reflect.textOf(o);
                    out.add(s == null ? "" : s);
                }
                if (!out.isEmpty()) return out;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------ writing

    /** REPLACES the whole book. @return a message for the player; ok[0] tells whether it worked */
    @SuppressWarnings("unchecked")
    public static String paste(Object screen, List<String> newPages, boolean[] ok) {
        ok[0] = false;
        String fail = "This book screen can't be filled automatically. Use Copy Page, then Ctrl+V in the book.";
        try {
            Class<?> c = screen.getClass();
            Field fPages = Reflect.field(c, "pages");
            Field fCur = Reflect.field(c, "currentPage");
            if (fPages == null || fCur == null || fCur.getType() != int.class) return fail;
            Object rawList = fPages.get(screen);
            if (!(rawList instanceof List)) return fail;
            List<Object> list = (List<Object>) rawList;
            for (Object o : list) {
                if (!(o instanceof String)) return fail; // not plain text pages (e.g. rich-text pages)
            }
            Object editBox = Reflect.get(screen, "editBox");
            Method setValue = null;
            if (editBox != null) {
                for (Method m : editBox.getClass().getMethods()) {
                    if (m.getName().equals("setValue") && m.getParameterCount() >= 1 && m.getParameterTypes()[0] == String.class) {
                        if (setValue == null || m.getParameterCount() < setValue.getParameterCount()) setValue = m;
                    }
                }
            }
            Object pageEdit = Reflect.get(screen, "pageEdit");
            Method toEnd = pageEdit == null ? null : Reflect.method(pageEdit.getClass(), "setCursorToEnd");
            if (setValue == null && toEnd == null) return fail; // can't refresh the visible text safely

            int count = Math.min(MAX_PAGES, newPages.size());
            if (count == 0) return "Nothing to paste.";
            list.clear();
            for (int i = 0; i < count; i++) {
                String p = newPages.get(i);
                list.add(p == null ? "" : p);
            }
            fCur.setInt(screen, 0);
            Field fMod = Reflect.field(c, "isModified");
            if (fMod != null && fMod.getType() == boolean.class) fMod.setBoolean(screen, true);
            for (String m : new String[]{"clearDisplayCache", "updateButtonVisibility", "updatePageContent"}) Reflect.call(screen, m);
            if (setValue != null) {
                Object[] args = new Object[setValue.getParameterCount()];
                args[0] = list.get(0);
                for (int i = 1; i < args.length; i++) args[i] = setValue.getParameterTypes()[i] == boolean.class ? Boolean.TRUE : null;
                setValue.invoke(editBox, args);
            }
            if (toEnd != null) toEnd.invoke(pageEdit);

            ok[0] = true;
            String msg = "Replaced your book with " + count + (count == 1 ? " page!" : " pages!");
            if (count < newPages.size()) msg += " (" + (newPages.size() - count) + " didn't fit)";
            return msg;
        } catch (Throwable t) {
            return fail;
        }
    }
}
