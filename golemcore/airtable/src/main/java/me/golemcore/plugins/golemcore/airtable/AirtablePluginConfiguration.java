package me.golemcore.plugins.golemcore.airtable;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        AirtableRecordsToolProvider.class,
        AirtablePluginSettingsContributor.class
})
public class AirtablePluginConfiguration {
}
