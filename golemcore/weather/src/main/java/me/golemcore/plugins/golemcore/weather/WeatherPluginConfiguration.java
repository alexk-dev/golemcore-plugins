package me.golemcore.plugins.golemcore.weather;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        WeatherToolProvider.class,
        WeatherPluginSettingsContributor.class
})
public class WeatherPluginConfiguration {
}
