package com.auditlab.mod.mixin;

import com.auditlab.mod.freecam.FreecamController;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Freecam: render chunks as for a spectator, so the occlusion culling does not black out the
 * world while the camera is inside blocks.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/WorldRenderer;updateCamera(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Z)V"),
        index = 2)
    private boolean auditlab$freecamSpectatorCulling(boolean spectator) {
        return spectator || FreecamController.get().isActive();
    }
}
