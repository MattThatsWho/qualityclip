package com.qualityclip.notify;

import com.qualityclip.data.Clip;
import com.qualityclip.data.ClipStore;
import com.qualityclip.data.Schedule;
import com.qualityclip.data.Settings;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;

/** Checks schedules once a second. Missed reminders (game was closed) fire once on startup. */
public final class ScheduleManager {
    private static int ticks = 0;

    private ScheduleManager() {}

    public static void tick(Minecraft mc) {
        if (++ticks % 20 != 0) return;
        check();
    }

    public static void check() {
        long now = System.currentTimeMillis();
        boolean dirty = false;

        for (Clip c : new ArrayList<>(ClipStore.clips())) {
            if (c.snoozeMs > 0 && now >= c.snoozeMs) {
                c.snoozeMs = 0;
                NotificationManager.push(c, Settings.get().sound);
                dirty = true;
            }
            Schedule s = c.schedule;
            if (s == null || now < s.nextFireMs) continue;

            // A "today" schedule that ran out while the game was closed just disappears.
            if (s.mode == Schedule.Mode.TODAY && s.endMs > 0 && now > s.endMs) {
                c.schedule = null;
                dirty = true;
                continue;
            }

            NotificationManager.push(c, s.sound);

            if (c.isReminder() && !"NONE".equals(c.repeat)) {
                long period = "WEEKLY".equals(c.repeat) ? 7L * 86_400_000L : 86_400_000L;
                long next = s.nextFireMs + period;
                while (next <= now) next += period;
                s.nextFireMs = next;
                c.doneMs = now;
            } else if (s.mode == Schedule.Mode.ONCE) {
                c.schedule = null;
                if (c.isReminder()) c.doneMs = now;
            } else {
                long step = Math.max(5, s.intervalMinutes) * 60_000L;
                long next = s.nextFireMs + step;
                while (next <= now) next += step; // skip reminders we missed
                s.nextFireMs = next;
                if (s.mode == Schedule.Mode.TODAY && s.endMs > 0 && next > s.endMs) {
                    c.schedule = null; // that was the last one for today
                }
            }
            dirty = true;
        }
        if (dirty) ClipStore.save();
    }
}
