package me.golemcore.plugins.golemcore.perplexitysonar;

import me.golemcore.plugin.api.extension.spi.PluginBootstrap;
import me.golemcore.plugin.api.extension.spi.PluginDescriptor;

public class PerplexitySonarPluginBootstrap implements PluginBootstrap {

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id("golemcore/perplexity-sonar")
                .provider("golemcore")
                .name("perplexity-sonar")
                .entrypoint(getClass().getName())
                .build();
    }

    @Override
    public Class<?> configurationClass() {
        return PerplexitySonarPluginConfiguration.class;
    }
}
