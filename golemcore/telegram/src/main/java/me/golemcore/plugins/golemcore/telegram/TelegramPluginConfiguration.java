package me.golemcore.plugins.golemcore.telegram;

import me.golemcore.plugins.golemcore.telegram.adapter.inbound.telegram.TelegramAdapter;
import me.golemcore.plugins.golemcore.telegram.adapter.outbound.confirmation.TelegramConfirmationAdapter;
import me.golemcore.plugins.golemcore.telegram.adapter.outbound.plan.TelegramPlanApprovalAdapter;
import me.golemcore.plugins.golemcore.telegram.service.TelegramSessionService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        TelegramAdapter.class,
        TelegramConfirmationAdapter.class,
        TelegramPlanApprovalAdapter.class,
        TelegramSessionService.class,
        TelegramPluginSettingsContributor.class
})
public class TelegramPluginConfiguration {
}
