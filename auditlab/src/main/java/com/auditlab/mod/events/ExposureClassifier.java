package com.auditlab.mod.events;

import com.auditlab.mod.analysis.model.ExposureContext;
import com.auditlab.mod.config.AuditLabConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

/** Decides where a dynamic event happened relative to what the player can legitimately see. */
public final class ExposureClassifier {
    private ExposureClassifier() {
    }

    /**
     * @return the context, or {@code null} if the event is close to the local player and should be
     *     ignored as self-inflicted (e.g. blocks the player is mining)
     */
    public static ExposureContext classify(ClientWorld world, double x, double y, double z, AuditLabConfig config) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        double r = config.ignoreRadiusAroundPlayer;
        if (player != null && r > 0 && player.squaredDistanceTo(x, y, z) < r * r) return null;

        int bx = MathHelper.floor(x);
        int by = MathHelper.floor(y);
        int bz = MathHelper.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return ExposureContext.UNLOADED_CHUNK;

        // WORLD_SURFACE is one above the highest non-air block; same rule as BlockScanner.
        int surfaceTop = world.getTopY(Heightmap.Type.WORLD_SURFACE, bx, bz) - 1;
        return by <= surfaceTop - config.buriedDepth ? ExposureContext.UNDERGROUND : ExposureContext.SURFACE;
    }
}
