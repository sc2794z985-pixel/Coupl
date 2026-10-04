package dev.simdistviz.network;

/**
 * Network state: the distances the currently connected server has sent to this client.
 *
 * <p>Fed exclusively by {@link dev.simdistviz.mixin.ClientPlayNetworkHandlerMixin}, i.e. by the server's
 * {@code GameJoinS2CPacket}, {@code SimulationDistanceS2CPacket} and {@code ChunkLoadDistanceS2CPacket}.
 * The local video settings are never consulted. Values live only in memory and are reset whenever a new
 * play connection starts or the current one ends, so nothing carries over from one server to the next.
 */
public final class ServerDistanceTracker {
	public static final int UNKNOWN = -1;

	/** Which packet last delivered a value; shown in the debug overlay. */
	public enum Source {
		NONE("-"),
		GAME_JOIN("GameJoinS2CPacket"),
		SIMULATION_DISTANCE("SimulationDistanceS2CPacket"),
		CHUNK_LOAD_DISTANCE("ChunkLoadDistanceS2CPacket");

		private final String packetName;

		Source(String packetName) {
			this.packetName = packetName;
		}

		public String packetName() {
			return packetName;
		}
	}

	/** Called when the simulation distance changes while connected (not on the initial join). */
	@FunctionalInterface
	public interface ChangeListener {
		void onSimulationDistanceChanged(int oldDistance, int newDistance);
	}

	private static int simulationDistance = UNKNOWN;
	private static int viewDistance = UNKNOWN;
	private static Source simulationSource = Source.NONE;
	private static Source viewSource = Source.NONE;
	private static long simulationUpdatedAtMillis;
	private static long viewUpdatedAtMillis;
	/** Bumped on every change so dependent caches know when to rebuild. */
	private static int revision;
	private static ChangeListener changeListener = (oldDistance, newDistance) -> { };

	private ServerDistanceTracker() {
	}

	public static void setChangeListener(ChangeListener listener) {
		changeListener = listener;
	}

	public static void onGameJoin(int serverSimulationDistance, int serverViewDistance) {
		// A join packet starts a fresh session (also on proxy server switches), so it is not reported as a change.
		simulationDistance = serverSimulationDistance;
		viewDistance = serverViewDistance;
		simulationSource = Source.GAME_JOIN;
		viewSource = Source.GAME_JOIN;
		simulationUpdatedAtMillis = viewUpdatedAtMillis = System.currentTimeMillis();
		revision++;
	}

	public static void onSimulationDistance(int serverSimulationDistance) {
		int previous = simulationDistance;
		simulationDistance = serverSimulationDistance;
		simulationSource = Source.SIMULATION_DISTANCE;
		simulationUpdatedAtMillis = System.currentTimeMillis();
		revision++;

		if (previous != UNKNOWN && previous != serverSimulationDistance) {
			changeListener.onSimulationDistanceChanged(previous, serverSimulationDistance);
		}
	}

	public static void onViewDistance(int serverViewDistance) {
		viewDistance = serverViewDistance;
		viewSource = Source.CHUNK_LOAD_DISTANCE;
		viewUpdatedAtMillis = System.currentTimeMillis();
		revision++;
	}

	/** Forget everything; called on connection start and disconnect. */
	public static void reset() {
		simulationDistance = UNKNOWN;
		viewDistance = UNKNOWN;
		simulationSource = Source.NONE;
		viewSource = Source.NONE;
		simulationUpdatedAtMillis = viewUpdatedAtMillis = 0L;
		revision++;
	}

	public static boolean hasSimulationDistance() {
		return simulationDistance != UNKNOWN;
	}

	public static boolean hasViewDistance() {
		return viewDistance != UNKNOWN;
	}

	public static int simulationDistance() {
		return simulationDistance;
	}

	public static int viewDistance() {
		return viewDistance;
	}

	public static Source simulationSource() {
		return simulationSource;
	}

	public static Source viewSource() {
		return viewSource;
	}

	public static long simulationUpdatedAtMillis() {
		return simulationUpdatedAtMillis;
	}

	public static long viewUpdatedAtMillis() {
		return viewUpdatedAtMillis;
	}

	public static int revision() {
		return revision;
	}
}
