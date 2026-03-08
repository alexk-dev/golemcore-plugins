package me.golemcore.plugins.golemcore.firecrawl;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        FirecrawlScrapeToolProvider.class,
        FirecrawlPluginSettingsContributor.class
})
public class FirecrawlPluginConfiguration {
}
