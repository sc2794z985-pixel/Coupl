package com.auditlab.mod.mixin;

import com.auditlab.mod.freecam.FreecamController;
import com.auditlab.mod.zoom.ZoomController;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the zoom factor to the field of view and hides the hand in freecam. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void auditlab$zoomFov(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(ZoomController.get().applyFov(cir.getReturnValueF(), System.nanoTime()));
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void auditlab$hideHandInFreecam(float tickProgress, boolean sleeping, Matrix4f positionMatrix, CallbackInfo ci) {
        if (FreecamController.get().isActive()) ci.cancel();
    }
}
