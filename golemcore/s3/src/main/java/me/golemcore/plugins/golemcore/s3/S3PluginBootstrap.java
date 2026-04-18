package me.golemcore.plugins.golemcore.s3;

import me.golemcore.plugin.api.extension.spi.PluginBootstrap;
import me.golemcore.plugin.api.extension.spi.PluginDescriptor;

public class S3PluginBootstrap implements PluginBootstrap {

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id("golemcore/s3")
                .provider("golemcore")
                .name("s3")
                .entrypoint(getClass().getName())
                .build();
    }

    @Override
    public Class<?> configurationClass() {
        return S3PluginConfiguration.class;
    }
}
