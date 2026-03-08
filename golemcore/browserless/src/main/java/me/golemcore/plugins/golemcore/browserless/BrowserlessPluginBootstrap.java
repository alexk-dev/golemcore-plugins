package me.golemcore.plugins.golemcore.browserless;

import me.golemcore.plugin.api.extension.spi.PluginBootstrap;
import me.golemcore.plugin.api.extension.spi.PluginDescriptor;

public class BrowserlessPluginBootstrap implements PluginBootstrap {

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id("golemcore/browserless")
                .provider("golemcore")
                .name("browserless")
                .entrypoint(getClass().getName())
                .build();
    }

    @Override
    public Class<?> configurationClass() {
        return BrowserlessPluginConfiguration.class;
    }
}
