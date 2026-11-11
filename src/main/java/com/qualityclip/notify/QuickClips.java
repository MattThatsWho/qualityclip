package com.qualityclip.notify;

import com.qualityclip.compat.Safe;
import com.qualityclip.data.Clip;
import com.qualityclip.data.ClipStore;
import com.qualityclip.data.QuickSlot;
import com.qualityclip.data.Settings;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Runs a Quick Clip slot: fill in the placeholders, then save a clip, copy to the clipboard, or both. */
public final class QuickClips {
    private QuickClips() {}

    /** @return a short result message */
    public static String run(int slotIndex) {
        QuickSlot s = Settings.get().quick.get(Math.max(0, Math.min(QuickSlot.COUNT - 1, slotIndex)));
        String text = Placeholders.expand(s.template).trim();
        if (text.isEmpty()) {
            String m = "Slot " + (slotIndex + 1) + " is empty. Set it up in QualityClip.";
            NotificationManager.info("Quick Clip", m);
            return m;
        }
        if (text.length() > 256) text = text.substring(0, 256);
        boolean save = s.mode == QuickSlot.SAVE || s.mode == QuickSlot.BOTH;
        boolean copy = s.mode == QuickSlot.COPY || s.mode == QuickSlot.BOTH;
        if (save) {
            Clip c = new Clip();
            c.name = (s.name.isBlank() ? "Quick Clip" : s.name) + " " + LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"));
            if (c.name.length() > 32) c.name = c.name.substring(0, 32);
            c.content = text;
            ClipStore.add(c);
        }
        boolean copied = copy && Safe.copy(text);
        String title = save && copied ? "Saved & copied!" : save ? "Saved a clip!" : copied ? "Copied!" : "Quick Clip";
        NotificationManager.info(title, text);
        return title + " " + text;
    }
}
