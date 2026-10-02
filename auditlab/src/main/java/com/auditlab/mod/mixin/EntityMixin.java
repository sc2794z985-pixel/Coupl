package com.auditlab.mod.mixin;

import com.auditlab.mod.freecam.FreecamController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: mouse movement turns the free camera instead of the local player. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void auditlab$freecamLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        FreecamController freecam = FreecamController.get();
        if (freecam.isActive() && (Object) this == MinecraftClient.getInstance().player) {
            freecam.look(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}
