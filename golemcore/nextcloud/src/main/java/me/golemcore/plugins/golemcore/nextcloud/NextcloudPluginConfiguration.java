package me.golemcore.plugins.golemcore.nextcloud;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = NextcloudPluginConfiguration.class)
public class NextcloudPluginConfiguration {
}
