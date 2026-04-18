package me.golemcore.plugins.golemcore.s3;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = S3PluginConfiguration.class)
public class S3PluginConfiguration {
}
