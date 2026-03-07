package me.golemcore.plugins.golemcore.mail;

import me.golemcore.plugins.golemcore.mail.tool.ImapToolProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        ImapToolProvider.class,
        MailPluginSettingsContributor.class
})
public class MailPluginConfiguration {
}
