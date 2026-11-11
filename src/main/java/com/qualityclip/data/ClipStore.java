package com.qualityclip.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Loads/saves all clips to config/qualityclip/clips.json */
public final class ClipStore {
    private static final Logger LOG = LoggerFactory.getLogger("QualityClip");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Clip> CLIPS = new ArrayList<>();

    private static final class Data {
        int version = 1;
        List<Clip> clips = new ArrayList<>();
    }

    private ClipStore() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("qualityclip").resolve("clips.json");
    }

    public static List<Clip> clips() {
        return CLIPS;
    }

    public static Clip find(String id) {
        for (Clip c : CLIPS) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    public static void add(Clip c) {
        CLIPS.add(c);
        save();
    }

    public static void remove(Clip c) {
        CLIPS.remove(c);
        save();
    }

    /** public so imports can reuse the same cleaning */
    public static void clean(Clip c) {
        sanitize(c);
    }

    public static void load() {
        CLIPS.clear();
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            Data d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
            if (d != null && d.clips != null) {
                for (Clip c : d.clips) {
                    if (c == null) continue;
                    sanitize(c);
                    CLIPS.add(c);
                }
            }
        } catch (Exception e) {
            LOG.error("Could not read clips.json - keeping a backup and starting fresh", e);
            try {
                Files.move(f, f.resolveSibling("clips.json.broken-" + System.currentTimeMillis()),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            CLIPS.clear();
        }
    }

    public static synchronized void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Data d = new Data();
            d.clips = new ArrayList<>(CLIPS);
            Path tmp = f.resolveSibling("clips.json.tmp");
            Files.writeString(tmp, GSON.toJson(d), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.error("Could not save clips.json", e);
        }
    }

    private static void sanitize(Clip c) {
        if (c.id == null || c.id.isBlank()) c.id = java.util.UUID.randomUUID().toString();
        if (c.name == null) c.name = Clip.DEFAULT_NAME;
        if (c.content == null) c.content = "";
        if (c.type == null || !(c.type.equals(Clip.TEXT) || c.type.equals(Clip.BOOK) || c.type.equals(Clip.REMINDER) || c.type.equals(Clip.SIGN))) c.type = Clip.TEXT;
        if (c.repeat == null) c.repeat = "NONE";
        if (c.isSign()) {
            while (c.pages().size() < 2) c.pages.add("");
        }
        if (c.isBook()) {
            c.pages();
            for (int i = 0; i < c.pages.size(); i++) {
                if (c.pages.get(i) == null) c.pages.set(i, "");
            }
        }
        if (c.schedule != null) {
            Schedule s = c.schedule;
            if (s.mode == null) s.mode = Schedule.Mode.ONCE;
            s.intervalMinutes = Math.max(5, Math.min(60, s.intervalMinutes));
        }
    }
}
