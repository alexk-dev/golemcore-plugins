package me.golemcore.plugins.golemcore.pinchtab;

import me.golemcore.plugin.api.extension.spi.PluginBootstrap;
import me.golemcore.plugin.api.extension.spi.PluginDescriptor;

public class PinchTabPluginBootstrap implements PluginBootstrap {

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id("golemcore/pinchtab")
                .provider("golemcore")
                .name("pinchtab")
                .entrypoint(getClass().getName())
                .build();
    }

    @Override
    public Class<?> configurationClass() {
        return PinchTabPluginConfiguration.class;
    }
}
