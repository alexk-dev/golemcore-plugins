package me.golemcore.plugins.golemcore.browserless;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        BrowserlessSmartScrapeToolProvider.class,
        BrowserlessPluginSettingsContributor.class
})
public class BrowserlessPluginConfiguration {
}
