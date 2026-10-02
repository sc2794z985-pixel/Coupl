package com.auditlab.mod.config;

import com.auditlab.mod.analysis.model.EventType;
import com.auditlab.mod.analysis.model.ScoreCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
    @TempDir
    Path dir;

    @Test
    void createsDefaultsAndRoundTrips() throws Exception {
        ConfigManager m = new ConfigManager(dir.resolve("auditlab.json"));
        AuditLabConfig c = m.load();
        assertTrue(Files.exists(m.file()));
        String json = Files.readString(m.file());
        assertTrue(json.contains("\"BLOCK_DENSITY\": 30"), json);
        assertTrue(json.contains("\"minecraft:crafting_table\""));

        c.highThreshold = 70;
        c.eventWeights.put(EventType.SOUND, 2.5);
        m.save();
        AuditLabConfig reloaded = new ConfigManager(m.file()).load();
        assertEquals(70, reloaded.highThreshold);
        assertEquals(2.5, reloaded.eventWeights.get(EventType.SOUND));
        assertEquals(30, reloaded.cap(ScoreCategory.BLOCK_DENSITY));
    }

    @Test
    void partialFileGetsDefaults() throws Exception {
        Path f = dir.resolve("auditlab.json");
        Files.writeString(f, "{\"overlayEnabled\": false, \"geometry\": {\"minVolume\": 20}}");
        AuditLabConfig c = new ConfigManager(f).load();
        assertFalse(c.overlayEnabled);
        assertEquals(20, c.geometry.minVolume);
        assertEquals(0.80, c.geometry.minFillRatio, 1e-9);
        assertEquals(60, c.highThreshold);
        assertFalse(c.blockWeights.isEmpty());
    }

    @Test
    void malformedFileIsPreservedAndDefaultsUsed() throws Exception {
        Path f = dir.resolve("auditlab.json");
        Files.writeString(f, "{ not json");
        ConfigManager m = new ConfigManager(f);
        AuditLabConfig c = m.load();
        assertNotNull(m.lastError());
        assertEquals("{ not json", Files.readString(f));
        assertTrue(c.overlayEnabled);
    }

    @Test
    void normalizeClampsValues() {
        AuditLabConfig c = new AuditLabConfig();
        c.highThreshold = 500;
        c.mediumThreshold = 200;
        c.renderDistanceChunks = -4;
        c.categoryCaps.put(ScoreCategory.GEOMETRY, 400);
        c.blockWeights.put("minecraft:lever", -3.0);
        c.geometry.maxVolume = 1;
        c.normalize();
        assertEquals(100, c.highThreshold);
        assertEquals(100, c.mediumThreshold);
        assertEquals(1, c.renderDistanceChunks);
        assertEquals(100, c.cap(ScoreCategory.GEOMETRY));
        assertEquals(0.0, c.blockWeight("minecraft:lever"));
        assertEquals(c.geometry.minVolume, c.geometry.maxVolume);
    }
}
