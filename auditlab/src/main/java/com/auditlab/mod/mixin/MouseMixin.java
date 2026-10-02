package com.auditlab.mod.mixin;

import com.auditlab.mod.freecam.FreecamController;
import com.auditlab.mod.zoom.ZoomController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

/**
 * In-game scroll wheel: adjusts the zoom while zooming, otherwise the freecam speed while in
 * freecam; the hotbar slot does not change in either case.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void auditlab$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (client.currentScreen != null || vertical == 0) return;
        if (ZoomController.get().scroll(Math.signum(vertical))) {
            ci.cancel();
            return;
        }
        FreecamController freecam = FreecamController.get();
        if (freecam.isActive()) {
            freecam.scrollSpeed(Math.signum(vertical));
            if (client.player != null) {
                client.player.sendMessage(Text.literal(String.format(Locale.ROOT, "Freecam speed %.2f", freecam.speed())), true);
            }
            ci.cancel();
        }
    }
}
