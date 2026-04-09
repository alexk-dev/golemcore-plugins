package me.golemcore.plugins.golemcore.airtable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirtablePluginConfigTest {

    @Test
    void shouldNormalizeDefaultsAndTrimStrings() {
        AirtablePluginConfig config = AirtablePluginConfig.builder()
                .enabled(null)
                .apiBaseUrl(" https://api.airtable.com/ ")
                .apiToken("   ")
                .baseId(" appBase ")
                .defaultTable(" Tasks ")
                .defaultView(null)
                .defaultMaxRecords(0)
                .allowWrite(null)
                .allowDelete(null)
                .typecast(null)
                .build();

        config.normalize();

        assertFalse(config.getEnabled());
        assertEquals("https://api.airtable.com", config.getApiBaseUrl());
        assertNull(config.getApiToken());
        assertEquals("appBase", config.getBaseId());
        assertEquals("Tasks", config.getDefaultTable());
        assertEquals("", config.getDefaultView());
        assertEquals(AirtablePluginConfig.DEFAULT_MAX_RECORDS, config.getDefaultMaxRecords());
        assertFalse(config.getAllowWrite());
        assertFalse(config.getAllowDelete());
        assertFalse(config.getTypecast());
    }

    @Test
    void shouldClampMaxRecordsToAirtableLimit() {
        AirtablePluginConfig config = AirtablePluginConfig.builder()
                .enabled(true)
                .defaultMaxRecords(500)
                .typecast(true)
                .build();

        config.normalize();

        assertEquals(AirtablePluginConfig.MAX_RECORDS_LIMIT, config.getDefaultMaxRecords());
        assertTrue(config.getTypecast());
    }
}
