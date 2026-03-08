package me.golemcore.plugins.golemcore.tavilysearch;

import me.golemcore.plugin.api.extension.spi.PluginBootstrap;
import me.golemcore.plugin.api.extension.spi.PluginDescriptor;

public class TavilySearchPluginBootstrap implements PluginBootstrap {

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id("golemcore/tavily-search")
                .provider("golemcore")
                .name("tavily-search")
                .entrypoint(getClass().getName())
                .build();
    }

    @Override
    public Class<?> configurationClass() {
        return TavilySearchPluginConfiguration.class;
    }
}
