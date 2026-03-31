package me.golemcore.plugins.golemcore.telegram.adapter.inbound.telegram;

import me.golemcore.plugin.api.extension.port.inbound.CommandPort;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugin.api.runtime.UserPreferencesService;
import me.golemcore.plugin.api.runtime.i18n.MessageService;
import me.golemcore.plugin.api.runtime.security.AllowlistValidator;
import me.golemcore.plugins.golemcore.telegram.TelegramPluginConfiguration;
import me.golemcore.plugins.golemcore.telegram.TelegramPluginSettingsContributor;
import me.golemcore.plugins.golemcore.telegram.adapter.outbound.confirmation.TelegramConfirmationAdapter;
import me.golemcore.plugins.golemcore.telegram.adapter.outbound.plan.TelegramPlanApprovalAdapter;
import me.golemcore.plugins.golemcore.telegram.service.TelegramSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class TelegramAdapterSpringWiringTest {

    @Test
    void shouldCreateTelegramAdapterBeanWithoutDefaultConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(TelegramPluginConfiguration.class, TestConfig.class);
            context.refresh();

            assertNotNull(context.getBean(TelegramAdapter.class));
            assertNotNull(context.getBean(TelegramConfirmationAdapter.class));
            assertNotNull(context.getBean(TelegramPlanApprovalAdapter.class));
            assertNotNull(context.getBean(TelegramSessionService.class));
            assertNotNull(context.getBean(TelegramPluginSettingsContributor.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        @Primary
        RuntimeConfigService runtimeConfigService() {
            return mock(RuntimeConfigService.class);
        }

        @Bean
        AllowlistValidator allowlistValidator() {
            return mock(AllowlistValidator.class);
        }

        @Bean
        UserPreferencesService userPreferencesService() {
            return mock(UserPreferencesService.class);
        }

        @Bean
        MessageService messageService() {
            return mock(MessageService.class);
        }

        @Bean
        CommandPort commandPort() {
            return mock(CommandPort.class);
        }

        @Bean
        me.golemcore.plugin.api.runtime.ModelSelectionService modelSelectionService() {
            return mock(me.golemcore.plugin.api.runtime.ModelSelectionService.class);
        }

        @Bean
        me.golemcore.plugin.api.runtime.AutoModeService autoModeService() {
            return mock(me.golemcore.plugin.api.runtime.AutoModeService.class);
        }

        @Bean
        me.golemcore.plugin.api.runtime.PlanService planService() {
            return mock(me.golemcore.plugin.api.runtime.PlanService.class);
        }

        @Bean
        me.golemcore.plugin.api.runtime.PlanExecutionService planExecutionService() {
            return mock(me.golemcore.plugin.api.runtime.PlanExecutionService.class);
        }

        @Bean
        me.golemcore.plugin.api.extension.port.outbound.SessionPort sessionPort() {
            return mock(me.golemcore.plugin.api.extension.port.outbound.SessionPort.class);
        }

        @Bean
        me.golemcore.plugin.api.runtime.ActiveSessionPointerService activeSessionPointerService() {
            return mock(me.golemcore.plugin.api.runtime.ActiveSessionPointerService.class);
        }

        @Bean
        me.golemcore.plugin.api.extension.port.outbound.VoicePort voicePort() {
            return mock(me.golemcore.plugin.api.extension.port.outbound.VoicePort.class);
        }
    }
}
