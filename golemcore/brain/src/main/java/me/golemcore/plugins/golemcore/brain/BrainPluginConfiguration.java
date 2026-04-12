package me.golemcore.plugins.golemcore.brain;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        BrainToolProvider.class,
        BrainPluginSettingsContributor.class
})
public class BrainPluginConfiguration {
}
