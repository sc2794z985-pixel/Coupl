package com.auditlab.mod.mixin;

import com.auditlab.mod.freecam.FreecamController;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam: movement keys fly the camera; the player receives no movement input and stands still.
 * Runs once per client tick, so it also advances the free camera.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void auditlab$freecamInput(CallbackInfo ci) {
        FreecamController freecam = FreecamController.get();
        if (!freecam.isActive()) return;
        PlayerInput in = this.playerInput;
        freecam.tick(in.forward(), in.backward(), in.left(), in.right(), in.jump(), in.sneak(), in.sprint());
        this.playerInput = PlayerInput.DEFAULT;
        this.movementVector = Vec2f.ZERO;
    }
}
