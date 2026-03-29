package me.golemcore.plugins.golemcore.obsidian;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.golemcore.plugin.api.extension.spi.PluginDescriptor;
import org.junit.jupiter.api.Test;

class ObsidianPluginBootstrapTest {

    @Test
    void shouldDescribeObsidianPlugin() {
        ObsidianPluginBootstrap bootstrap = new ObsidianPluginBootstrap();

        PluginDescriptor descriptor = bootstrap.descriptor();

        assertEquals("golemcore/obsidian", descriptor.getId());
        assertEquals("golemcore", descriptor.getProvider());
        assertEquals("obsidian", descriptor.getName());
        assertEquals(ObsidianPluginConfiguration.class, bootstrap.configurationClass());
    }
}
