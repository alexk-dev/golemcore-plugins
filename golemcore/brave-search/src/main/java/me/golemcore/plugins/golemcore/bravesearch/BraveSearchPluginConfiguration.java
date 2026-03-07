package me.golemcore.plugins.golemcore.bravesearch;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        BraveSearchToolProvider.class,
        BraveSearchPluginSettingsContributor.class
})
public class BraveSearchPluginConfiguration {
}
