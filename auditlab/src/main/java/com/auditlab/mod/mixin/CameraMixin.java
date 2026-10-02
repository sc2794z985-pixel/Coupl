package com.auditlab.mod.mixin;

import com.auditlab.mod.freecam.FreecamController;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam: after vanilla positions the camera, move it to the free camera instead. Marking it
 * third-person makes the world renderer draw the player's own body.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean thirdPerson;

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void auditlab$freecam(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
                                  float tickProgress, CallbackInfo ci) {
        FreecamController freecam = FreecamController.get();
        if (!freecam.isActive()) return;
        setRotation(freecam.yaw(), freecam.pitch());
        setPos(freecam.x(tickProgress), freecam.y(tickProgress), freecam.z(tickProgress));
        this.thirdPerson = true;
    }
}
