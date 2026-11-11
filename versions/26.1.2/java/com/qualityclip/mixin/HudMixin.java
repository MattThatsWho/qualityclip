package com.qualityclip.mixin;

import com.qualityclip.notify.HudHook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HUD toast for 26.1.x: the HUD was drawn by Gui. Optional config; skipped if the target doesn't match. */
@Mixin(Gui.class)
public abstract class HudMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void qc$hud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        HudHook.render(graphics);
    }
}
