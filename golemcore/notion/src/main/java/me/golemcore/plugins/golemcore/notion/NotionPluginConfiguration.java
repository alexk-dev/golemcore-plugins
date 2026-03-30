package me.golemcore.plugins.golemcore.notion;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = NotionPluginConfiguration.class)
public class NotionPluginConfiguration {
}
