package com.qualityclip.compat;

import net.minecraft.client.Minecraft;

public final class HudSize {
    private HudSize() {}

    public static int scaledWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public static int scaledHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}
