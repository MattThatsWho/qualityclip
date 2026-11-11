package com.qualityclip.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Clip {
    public static final String DEFAULT_NAME = "New Clip";
    public static final String TEXT = "TEXT";
    public static final String BOOK = "BOOK";
    public static final String REMINDER = "REMINDER";
    public static final String SIGN = "SIGN";

    public String id = UUID.randomUUID().toString();
    public String name = DEFAULT_NAME;
    /** chat-text clips */
    public String content = "";
    /** TEXT = one chat message, BOOK = multi-page notes */
    public String type = TEXT;
    /** notes clips: one string per page (may contain section-sign colour codes) */
    public List<String> pages;
    public long createdMs = System.currentTimeMillis();
    /** reminders: when it last fired (0 = still waiting) */
    public long doneMs;
    /** reminders: NONE / DAILY / WEEKLY */
    public String repeat = "NONE";
    /** snoozed: remind again at this time (0 = not snoozed) */
    public long snoozeMs;
    public boolean favorite;
    /** null = no schedule */
    public Schedule schedule;

    public boolean isBook() {
        return BOOK.equals(type);
    }

    public boolean isSign() {
        return SIGN.equals(type);
    }

    public boolean isReminder() {
        return REMINDER.equals(type);
    }

    /** never empty, never null */
    public List<String> pages() {
        if (pages == null) pages = new ArrayList<>();
        if (pages.isEmpty()) pages.add("");
        return pages;
    }

    public boolean isBlank() {
        if (isSign()) {
            for (String p : pages()) if (p != null && !p.isBlank()) return false;
            return true;
        }
        if (!isBook()) return (content == null || content.isBlank()) && (!isReminder() || schedule == null);
        for (String p : pages()) {
            if (p != null && !p.isBlank()) return false;
        }
        return true;
    }
}
