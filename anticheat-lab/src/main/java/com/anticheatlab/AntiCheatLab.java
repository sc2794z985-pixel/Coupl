package com.anticheatlab;

import com.anticheatlab.data.ObservationStore;
import com.anticheatlab.modules.ChunkAuditor;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;

/**
 * Meteor addon entry point (declared under {@code entrypoints.meteor} in fabric.mod.json).
 *
 * <p>AntiCheat Lab is a passive auditor: it only reads packets the client already received.
 * It never sends packets, moves the player, or interacts with the world.
 */
public class AntiCheatLab extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("AntiCheat Lab", Items.SPYGLASS.getDefaultStack());

    private static final ObservationStore STORE = new ObservationStore();

    public static ObservationStore store() {
        return STORE;
    }

    @Override
    public void onInitialize() {
        LOG.info("Initializing AntiCheat Lab (passive mode)");
        Modules.get().add(new ChunkAuditor());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.anticheatlab";
    }
}
