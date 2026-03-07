package me.golemcore.plugins.golemcore.elevenlabs;

import me.golemcore.plugin.api.extension.spi.PluginBootstrap;
import me.golemcore.plugin.api.extension.spi.PluginDescriptor;

public class ElevenLabsPluginBootstrap implements PluginBootstrap {

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id("golemcore/elevenlabs")
                .provider("golemcore")
                .name("elevenlabs")
                .entrypoint(getClass().getName())
                .build();
    }

    @Override
    public Class<?> configurationClass() {
        return ElevenLabsPluginConfiguration.class;
    }
}
