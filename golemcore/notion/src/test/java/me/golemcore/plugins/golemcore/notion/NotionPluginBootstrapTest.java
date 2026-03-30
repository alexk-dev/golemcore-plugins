package me.golemcore.plugins.golemcore.notion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.golemcore.plugin.api.extension.spi.PluginDescriptor;
import org.junit.jupiter.api.Test;

class NotionPluginBootstrapTest {

    @Test
    void shouldDescribeNotionPlugin() {
        NotionPluginBootstrap bootstrap = new NotionPluginBootstrap();

        PluginDescriptor descriptor = bootstrap.descriptor();

        assertEquals("golemcore/notion", descriptor.getId());
        assertEquals("golemcore", descriptor.getProvider());
        assertEquals("notion", descriptor.getName());
        assertEquals(NotionPluginConfiguration.class, bootstrap.configurationClass());
    }
}
