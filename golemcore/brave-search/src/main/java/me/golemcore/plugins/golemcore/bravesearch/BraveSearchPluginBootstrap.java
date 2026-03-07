package me.golemcore.plugins.golemcore.bravesearch;

import me.golemcore.plugin.api.extension.spi.PluginBootstrap;
import me.golemcore.plugin.api.extension.spi.PluginDescriptor;

public class BraveSearchPluginBootstrap implements PluginBootstrap {

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id("golemcore/brave-search")
                .provider("golemcore")
                .name("brave-search")
                .entrypoint(getClass().getName())
                .build();
    }

    @Override
    public Class<?> configurationClass() {
        return BraveSearchPluginConfiguration.class;
    }
}
