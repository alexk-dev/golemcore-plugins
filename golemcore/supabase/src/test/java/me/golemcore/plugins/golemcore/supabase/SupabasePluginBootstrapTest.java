package me.golemcore.plugins.golemcore.supabase;

import me.golemcore.plugin.api.extension.spi.PluginDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupabasePluginBootstrapTest {

    @Test
    void shouldDescribeSupabasePlugin() {
        SupabasePluginBootstrap bootstrap = new SupabasePluginBootstrap();

        PluginDescriptor descriptor = bootstrap.descriptor();

        assertEquals("golemcore/supabase", descriptor.getId());
        assertEquals("golemcore", descriptor.getProvider());
        assertEquals("supabase", descriptor.getName());
        assertEquals(SupabasePluginConfiguration.class, bootstrap.configurationClass());
    }
}
