package com.qualityclip;

import com.qualityclip.compat.Compat;
import com.qualityclip.notify.EditorHook;
import com.qualityclip.notify.QuickClips;
import com.qualityclip.data.ClipStore;
import com.qualityclip.data.Settings;
import com.qualityclip.notify.NotificationManager;
import com.qualityclip.notify.ScheduleManager;
import com.qualityclip.screen.ClipboardScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QualityClipMod implements ClientModInitializer {
    public static final String MOD_ID = "qualityclip";
    public static final Logger LOGGER = LoggerFactory.getLogger("QualityClip");

    private static KeyMapping openKey;
    private static final KeyMapping[] quickKeys = new KeyMapping[4];
    private static boolean tickBroken = false;

    @Override
    public void onInitializeClient() {
        try {
            ClipStore.load();
            Settings.load();
        } catch (Throwable t) {
            LOGGER.error("QualityClip could not load its saved data", t);
        }

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        try {
            KeyMapping km = Compat.openKey(category);
            if (km != null) openKey = KeyMappingHelper.registerKeyMapping(km);
        } catch (Throwable t) {
            LOGGER.error("QualityClip could not register its hotkey (Mod Menu and the chat button still work)", t);
        }

        for (int i = 0; i < quickKeys.length; i++) {
            try {
                KeyMapping q = Compat.key("key.qualityclip.quick" + (i + 1), -1, 0, category);
                if (q != null) quickKeys[i] = KeyMappingHelper.registerKeyMapping(q);
            } catch (Throwable t) {
                LOGGER.error("QualityClip could not register Quick Clip key " + (i + 1), t);
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (tickBroken) return;
            try {
                while (openKey != null && openKey.consumeClick()) {
                    if (Compat.screen() == null) {
                        Compat.setScreen(new ClipboardScreen(null));
                    }
                }
                EditorHook.tick();
                for (int i = 0; i < quickKeys.length; i++) {
                    KeyMapping q = quickKeys[i];
                    while (q != null && q.consumeClick()) {
                        if (Compat.screen() == null) QuickClips.run(i);
                    }
                }
                ScheduleManager.tick(client);
                NotificationManager.tick();
            } catch (Throwable t) {
                tickBroken = true;
                LOGGER.error("QualityClip tick disabled after an error", t);
            }
        });

        LOGGER.info("QualityClip loaded with {} clip(s)", ClipStore.clips().size());
    }
}
