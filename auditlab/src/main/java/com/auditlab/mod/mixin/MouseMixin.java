package com.auditlab.mod.mixin;

import com.auditlab.mod.zoom.ZoomController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While zooming in-game, the scroll wheel adjusts the zoom instead of the hotbar slot. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void auditlab$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (client.currentScreen == null && ZoomController.get().scroll(Math.signum(vertical))) ci.cancel();
    }
}
