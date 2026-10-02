/**
 * Phase 2: passive packet scanners. Each scanner subscribes to inbound packets the client
 * already received (chunk data, block/section updates, block entity data, block events,
 * sounds), translates them into {@link com.anticheatlab.model.LeakEvent}s and records them in
 * the {@link com.anticheatlab.data.ObservationStore}. Scanners never send packets.
 */
package com.anticheatlab.scanner;
