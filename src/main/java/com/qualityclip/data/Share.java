package com.qualityclip.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** JSON import / export. A file may hold one clip or many; import adds whatever it finds. */
public final class Share {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class FileData {
        String format = "qualityclip";
        int version = 1;
        List<Clip> clips = new ArrayList<>();
    }

    private Share() {}

    public static Path root() {
        return FabricLoader.getInstance().getConfigDir().resolve("qualityclip");
    }

    public static Path exportDir() {
        return root().resolve("exports");
    }

    public static Path importDir() {
        return root().resolve("imports");
    }

    private static String safe(String s) {
        String t = s == null ? "clips" : s.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (t.isBlank()) t = "clips";
        return t.length() > 40 ? t.substring(0, 40) : t;
    }

    /** @return the file written, or null on failure */
    public static Path export(List<Clip> clips, String baseName) {
        try {
            Files.createDirectories(exportDir());
            FileData d = new FileData();
            d.clips = new ArrayList<>(clips);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path f = exportDir().resolve("qualityclip-" + safe(baseName) + "-" + stamp + ".json");
            Files.writeString(f, GSON.toJson(d), StandardCharsets.UTF_8);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    /** @return how many clips were added from one file, or -1 if the file couldn't be read */
    public static int importFile(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            FileData d = GSON.fromJson(json, FileData.class);
            int n = 0;
            if (d != null && d.clips != null && !d.clips.isEmpty()) {
                for (Clip c : d.clips) {
                    if (c == null) continue;
                    ClipStore.clean(c);
                    c.id = UUID.randomUUID().toString(); // never collide with what you already have
                    c.snoozeMs = 0;
                    ClipStore.clips().add(c);
                    n++;
                }
            } else {
                Clip c = GSON.fromJson(json, Clip.class); // a bare single clip
                if (c != null && c.type != null && (c.name != null || c.content != null || c.pages != null)) {
                    ClipStore.clean(c);
                    c.id = UUID.randomUUID().toString();
                    ClipStore.clips().add(c);
                    n = 1;
                }
            }
            if (n > 0) ClipStore.save();
            return n;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** imports every .json from the given paths (files, or folders); returns {clips added, files failed} */
    public static int[] importPaths(List<Path> paths) {
        int added = 0, failed = 0;
        for (Path p : paths) {
            try {
                if (Files.isDirectory(p)) {
                    try (Stream<Path> s = Files.list(p)) {
                        for (Path f : (Iterable<Path>) s.filter(x -> x.toString().toLowerCase().endsWith(".json"))::iterator) {
                            int n = importFile(f);
                            if (n < 0) failed++;
                            else added += n;
                        }
                    }
                } else if (p.toString().toLowerCase().endsWith(".json")) {
                    int n = importFile(p);
                    if (n < 0) failed++;
                    else added += n;
                }
            } catch (IOException e) {
                failed++;
            }
        }
        return new int[]{added, failed};
    }

    /** imports everything waiting in config/qualityclip/imports and marks the files as done */
    public static int[] importFolder() {
        try {
            Files.createDirectories(importDir());
        } catch (IOException ignored) {
        }
        int added = 0, failed = 0;
        try (Stream<Path> s = Files.list(importDir())) {
            for (Path f : (Iterable<Path>) s.filter(x -> x.toString().toLowerCase().endsWith(".json"))::iterator) {
                int n = importFile(f);
                if (n < 0) {
                    failed++;
                } else {
                    added += n;
                    try {
                        Files.move(f, f.resolveSibling(f.getFileName() + ".imported"), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (IOException e) {
            failed++;
        }
        return new int[]{added, failed};
    }
}
