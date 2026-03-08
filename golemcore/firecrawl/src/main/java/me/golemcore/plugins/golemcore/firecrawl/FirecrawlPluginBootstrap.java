package me.golemcore.plugins.golemcore.firecrawl;

import me.golemcore.plugin.api.extension.spi.PluginBootstrap;
import me.golemcore.plugin.api.extension.spi.PluginDescriptor;

public class FirecrawlPluginBootstrap implements PluginBootstrap {

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id("golemcore/firecrawl")
                .provider("golemcore")
                .name("firecrawl")
                .entrypoint(getClass().getName())
                .build();
    }

    @Override
    public Class<?> configurationClass() {
        return FirecrawlPluginConfiguration.class;
    }
}
