package me.golemcore.plugins.golemcore.nextcloud;

import me.golemcore.plugin.api.extension.spi.PluginBootstrap;
import me.golemcore.plugin.api.extension.spi.PluginDescriptor;

public class NextcloudPluginBootstrap implements PluginBootstrap {

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id("golemcore/nextcloud")
                .provider("golemcore")
                .name("nextcloud")
                .entrypoint(getClass().getName())
                .build();
    }

    @Override
    public Class<?> configurationClass() {
        return NextcloudPluginConfiguration.class;
    }
}
