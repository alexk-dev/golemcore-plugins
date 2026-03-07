package me.golemcore.plugins.golemcore.browser;

import me.golemcore.plugins.golemcore.browser.adapter.outbound.PlaywrightBrowserClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        PlaywrightBrowserClient.class,
        BrowserPluginSettingsContributor.class,
        BrowserToolProvider.class
})
public class BrowserPluginConfiguration {
}
