package me.golemcore.plugins.golemcore.slack;

import me.golemcore.plugins.golemcore.slack.adapter.inbound.slack.SlackAdapter;
import me.golemcore.plugins.golemcore.slack.support.SlackBoltSocketGateway;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        SlackAdapter.class,
        SlackPluginConfigService.class,
        SlackPluginSettingsContributor.class,
        SlackBoltSocketGateway.class
})
public class SlackPluginConfiguration {
}
