package me.golemcore.plugins.golemcore.supabase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupabasePluginConfigTest {

    @Test
    void shouldNormalizeDefaultsAndTrimStrings() {
        SupabasePluginConfig config = SupabasePluginConfig.builder()
                .enabled(null)
                .projectUrl(" https://project.supabase.co/ ")
                .apiKey("   ")
                .defaultSchema(" ")
                .defaultTable(" tasks ")
                .defaultSelect(" ")
                .defaultLimit(0)
                .allowWrite(null)
                .allowDelete(null)
                .build();

        config.normalize();

        assertFalse(config.getEnabled());
        assertEquals("https://project.supabase.co", config.getProjectUrl());
        assertNull(config.getApiKey());
        assertEquals(SupabasePluginConfig.DEFAULT_SCHEMA, config.getDefaultSchema());
        assertEquals("tasks", config.getDefaultTable());
        assertEquals(SupabasePluginConfig.DEFAULT_SELECT, config.getDefaultSelect());
        assertEquals(SupabasePluginConfig.DEFAULT_LIMIT, config.getDefaultLimit());
        assertFalse(config.getAllowWrite());
        assertFalse(config.getAllowDelete());
    }

    @Test
    void shouldClampLimitToMaximum() {
        SupabasePluginConfig config = SupabasePluginConfig.builder()
                .enabled(true)
                .defaultLimit(10_000)
                .allowWrite(true)
                .build();

        config.normalize();

        assertEquals(SupabasePluginConfig.MAX_LIMIT, config.getDefaultLimit());
        assertTrue(config.getAllowWrite());
    }
}
