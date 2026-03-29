package me.golemcore.plugins.golemcore.obsidian;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = ObsidianPluginConfiguration.class)
public class ObsidianPluginConfiguration {
}
