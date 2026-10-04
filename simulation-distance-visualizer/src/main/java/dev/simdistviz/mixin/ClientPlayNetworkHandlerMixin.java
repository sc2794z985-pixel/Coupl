package dev.simdistviz.mixin;

import dev.simdistviz.network.ServerDistanceTracker;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkLoadDistanceS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.SimulationDistanceS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only observer of the three packets through which a server tells the client its distances.
 *
 * <p>Every injection is at {@code TAIL}: vanilla has already handled the packet completely, and we only copy
 * the values it stored. Nothing is cancelled, modified, or sent. The handlers start with
 * {@code NetworkThreadUtils.forceMainThread}, which aborts the netty-thread call and re-runs the method on the
 * client thread, so {@code TAIL} is only ever reached on the client thread.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
	/** Set by vanilla from {@code GameJoinS2CPacket} and {@code SimulationDistanceS2CPacket}. */
	@Shadow
	private int simulationDistance;

	@Inject(method = "onGameJoin", at = @At("TAIL"))
	private void simdistviz$afterGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
		ServerDistanceTracker.onGameJoin(this.simulationDistance, packet.viewDistance());
	}

	@Inject(method = "onSimulationDistance", at = @At("TAIL"))
	private void simdistviz$afterSimulationDistance(SimulationDistanceS2CPacket packet, CallbackInfo ci) {
		ServerDistanceTracker.onSimulationDistance(this.simulationDistance);
	}

	@Inject(method = "onChunkLoadDistance", at = @At("TAIL"))
	private void simdistviz$afterChunkLoadDistance(ChunkLoadDistanceS2CPacket packet, CallbackInfo ci) {
		ServerDistanceTracker.onViewDistance(packet.getDistance());
	}
}
