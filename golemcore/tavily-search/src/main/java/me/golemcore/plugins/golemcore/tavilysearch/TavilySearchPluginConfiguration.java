package me.golemcore.plugins.golemcore.tavilysearch;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        TavilySearchToolProvider.class,
        TavilySearchPluginSettingsContributor.class
})
public class TavilySearchPluginConfiguration {
}
