package com.qualityclip.notify;

import net.minecraft.client.gui.components.EditBox;

/** Small bridge between the clipboard screen and the chat box. */
public final class ChatState {
    /** Text the player had typed before opening the clipboard (restored when chat reopens). */
    public static String saved;
    /** Clip text to drop into the chat box next time chat initialises. */
    public static String pending;

    private ChatState() {}

    public static String sanitize(String text) {
        return text == null ? "" : text.replaceAll("[\\r\\n]+", " ");
    }

    public static void append(EditBox input, String text) {
        input.setValue(input.getValue() + sanitize(text));
    }

    /** Called at the end of ChatScreen.init() */
    public static void applyOnInit(EditBox input) {
        if (saved != null) {
            input.setValue(saved);
            saved = null;
        }
        if (pending != null) {
            append(input, pending);
            pending = null;
        }
    }
}
