package me.golemcore.plugins.golemcore.supabase;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        SupabaseRowsToolProvider.class,
        SupabasePluginSettingsContributor.class
})
public class SupabasePluginConfiguration {
}
