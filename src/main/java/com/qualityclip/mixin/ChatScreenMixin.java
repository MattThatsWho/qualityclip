package com.qualityclip.mixin;

import com.qualityclip.notify.ChatHook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Thin forwarder: all logic (and all error handling) lives in ChatHook. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void qc$render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ChatHook.render((Screen) (Object) this, g, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void qc$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ChatHook.click((Screen) (Object) this, event)) {
            cir.setReturnValue(true);
        }
    }
}
