package com.qualityclip.mixin;

import com.qualityclip.notify.ChatHook;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mouse-wheel scrolling for the reminder panel. Its own optional config so it can never affect the rest. */
@Mixin(ChatScreen.class)
public abstract class ChatScrollMixin {
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
    private void qc$scroll(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (ChatHook.scroll((Screen) (Object) this, mouseX, mouseY, scrollY)) {
            cir.setReturnValue(true);
        }
    }
}
