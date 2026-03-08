package me.golemcore.plugins.golemcore.perplexitysonar;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        PerplexityAskToolProvider.class,
        PerplexitySonarPluginSettingsContributor.class
})
public class PerplexitySonarPluginConfiguration {
}
