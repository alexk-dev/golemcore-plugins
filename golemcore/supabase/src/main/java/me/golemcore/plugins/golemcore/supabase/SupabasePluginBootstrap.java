package me.golemcore.plugins.golemcore.supabase;

import me.golemcore.plugin.api.extension.spi.PluginBootstrap;
import me.golemcore.plugin.api.extension.spi.PluginDescriptor;

public class SupabasePluginBootstrap implements PluginBootstrap {

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id("golemcore/supabase")
                .provider("golemcore")
                .name("supabase")
                .entrypoint(getClass().getName())
                .build();
    }

    @Override
    public Class<?> configurationClass() {
        return SupabasePluginConfiguration.class;
    }
}
