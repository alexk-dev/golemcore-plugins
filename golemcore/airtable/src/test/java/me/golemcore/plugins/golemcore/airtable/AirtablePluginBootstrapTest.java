package me.golemcore.plugins.golemcore.airtable;

import me.golemcore.plugin.api.extension.spi.PluginDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AirtablePluginBootstrapTest {

    @Test
    void shouldDescribeAirtablePlugin() {
        AirtablePluginBootstrap bootstrap = new AirtablePluginBootstrap();

        PluginDescriptor descriptor = bootstrap.descriptor();

        assertEquals("golemcore/airtable", descriptor.getId());
        assertEquals("golemcore", descriptor.getProvider());
        assertEquals("airtable", descriptor.getName());
        assertEquals(AirtablePluginConfiguration.class, bootstrap.configurationClass());
    }
}
