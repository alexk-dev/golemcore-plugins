package me.golemcore.plugins.golemcore.brain;

import me.golemcore.plugin.api.extension.spi.PluginDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrainPluginBootstrapTest {

    @Test
    void shouldExposeBrainPluginDescriptor() {
        BrainPluginBootstrap bootstrap = new BrainPluginBootstrap();

        PluginDescriptor descriptor = bootstrap.descriptor();

        assertEquals("golemcore/brain", descriptor.getId());
        assertEquals("golemcore", descriptor.getProvider());
        assertEquals("brain", descriptor.getName());
        assertEquals(BrainPluginConfiguration.class, bootstrap.configurationClass());
    }
}
