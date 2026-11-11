package com.qualityclip.notify;

import com.qualityclip.QualityClipMod;
import com.qualityclip.compat.HudSize;
import com.qualityclip.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudHook {
    private static boolean broken = false;

    private HudHook() {}

    public static void render(GuiGraphicsExtractor g) {
        if (broken || !NotificationManager.hasPending()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (Compat.screen() != null) return; // chat shows the reminder panel instead
            NotificationManager.renderHud(g, mc.font, HudSize.scaledWidth(), HudSize.scaledHeight());
        } catch (Throwable t) {
            broken = true;
            QualityClipMod.LOGGER.error("QualityClip HUD toast disabled after an error", t);
        }
    }
}
