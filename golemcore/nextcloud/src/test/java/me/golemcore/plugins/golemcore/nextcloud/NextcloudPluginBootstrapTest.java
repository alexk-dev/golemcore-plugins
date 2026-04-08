package me.golemcore.plugins.golemcore.nextcloud;

import me.golemcore.plugin.api.extension.spi.PluginDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NextcloudPluginBootstrapTest {

    @Test
    void shouldDescribeNextcloudPlugin() {
        NextcloudPluginBootstrap bootstrap = new NextcloudPluginBootstrap();

        PluginDescriptor descriptor = bootstrap.descriptor();

        assertEquals("golemcore/nextcloud", descriptor.getId());
        assertEquals("golemcore", descriptor.getProvider());
        assertEquals("nextcloud", descriptor.getName());
        assertEquals(NextcloudPluginConfiguration.class, bootstrap.configurationClass());
    }
}
