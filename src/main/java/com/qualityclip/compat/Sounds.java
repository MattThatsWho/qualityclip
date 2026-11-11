package com.qualityclip.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public final class Sounds {
    private Sounds() {}

    /** the same "ding" vanilla toasts use; falls back to the XP orb pickup sound */
    public static void ping(float pitch) {
        try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, pitch));
            return;
        } catch (Throwable ignored) {
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch));
    }
}
