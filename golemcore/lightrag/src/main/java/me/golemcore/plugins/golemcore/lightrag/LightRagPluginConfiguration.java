package me.golemcore.plugins.golemcore.lightrag;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        LightRagProvider.class,
        LightRagPluginSettingsContributor.class
})
public class LightRagPluginConfiguration {
}
