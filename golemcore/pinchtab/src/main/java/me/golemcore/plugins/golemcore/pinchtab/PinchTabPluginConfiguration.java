package me.golemcore.plugins.golemcore.pinchtab;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        PinchTabHealthToolProvider.class,
        PinchTabPluginSettingsContributor.class
})
public class PinchTabPluginConfiguration {
}
