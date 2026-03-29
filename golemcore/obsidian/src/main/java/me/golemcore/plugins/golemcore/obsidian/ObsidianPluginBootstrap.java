package me.golemcore.plugins.golemcore.obsidian;

import me.golemcore.plugin.api.extension.spi.PluginBootstrap;
import me.golemcore.plugin.api.extension.spi.PluginDescriptor;

public class ObsidianPluginBootstrap implements PluginBootstrap {

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id("golemcore/obsidian")
                .provider("golemcore")
                .name("obsidian")
                .entrypoint(getClass().getName())
                .build();
    }

    @Override
    public Class<?> configurationClass() {
        return ObsidianPluginConfiguration.class;
    }
}
