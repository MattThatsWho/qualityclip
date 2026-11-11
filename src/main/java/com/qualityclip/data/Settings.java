package com.qualityclip.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** User settings: config/qualityclip/settings.json */
public final class Settings {
    public enum NotifyStyle { STANDARD, BOLD, SUPER }

    private static final Logger LOG = LoggerFactory.getLogger("QualityClip");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Settings instance = new Settings();

    public NotifyStyle style = NotifyStyle.STANDARD;
    /** how long a reminder stays on the HUD */
    public int toastSeconds = 8;
    public boolean sound = true;
    /** notes editor shows two pages side by side */
    public boolean twoPageView = false;
    /** show reminder times as 24-hour */
    public boolean clock24 = false;
    /** how long the snooze button waits */
    public int snoozeMinutes = 10;
    /** the four Quick Clip slots (hotkeys are set in Controls) */
    public java.util.List<QuickSlot> quick = QuickSlot.defaults();

    public static Settings get() {
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("qualityclip").resolve("settings.json");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            Settings s = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Settings.class);
            if (s != null) {
                if (s.style == null) s.style = NotifyStyle.STANDARD;
                s.toastSeconds = Math.max(3, Math.min(30, s.toastSeconds));
                s.snoozeMinutes = Math.max(1, Math.min(60, s.snoozeMinutes == 0 ? 10 : s.snoozeMinutes));
                s.quick = QuickSlot.fix(s.quick);
                instance = s;
            }
        } catch (Exception e) {
            LOG.error("Could not read settings.json - using defaults", e);
        }
    }

    public static synchronized void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Could not save settings.json", e);
        }
    }
}
