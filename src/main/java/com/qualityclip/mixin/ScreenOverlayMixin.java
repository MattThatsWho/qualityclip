package com.qualityclip.mixin;

import com.qualityclip.notify.EditorHook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs at the end of Screen.extractRenderState (the base class every screen goes through, including the
 * Book & Quill screen with or without Scribble) so QualityClip's book buttons get their icons.
 */
@Mixin(Screen.class)
public abstract class ScreenOverlayMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void qc$overlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Object self = this;
        if (EditorHook.kind(self) != EditorHook.NONE) {
            EditorHook.overlay((Screen) self, g, mouseX, mouseY);
        }
    }
}
