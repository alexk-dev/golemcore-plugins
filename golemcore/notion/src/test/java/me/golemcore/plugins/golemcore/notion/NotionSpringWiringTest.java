package me.golemcore.plugins.golemcore.notion;

import me.golemcore.plugin.api.extension.port.outbound.SessionPort;
import me.golemcore.plugin.api.runtime.ActiveSessionPointerService;
import me.golemcore.plugin.api.runtime.RagIngestionService;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugins.golemcore.notion.support.NotionReindexCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class NotionSpringWiringTest {

    @Test
    void shouldCreateNotionBeansWithoutDefaultConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(NotionPluginConfiguration.class, TestConfig.class);
            context.refresh();

            assertNotNull(context.getBean(NotionReindexCoordinator.class));
            assertNotNull(context.getBean(NotionPluginSettingsContributor.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @SuppressWarnings("PMD.TestClassWithoutTestCases")
    static class TestConfig {

        @Bean
        @Primary
        RuntimeConfigService runtimeConfigService() {
            return mock(RuntimeConfigService.class);
        }

        @Bean
        RagIngestionService ragIngestionService() {
            return mock(RagIngestionService.class);
        }

        @Bean
        SessionPort sessionPort() {
            return mock(SessionPort.class);
        }

        @Bean
        me.golemcore.plugin.api.runtime.PluginConfigurationService pluginConfigurationService() {
            return mock(me.golemcore.plugin.api.runtime.PluginConfigurationService.class);
        }

        @Bean
        me.golemcore.plugin.api.runtime.PlanExecutionService planExecutionService() {
            return mock(me.golemcore.plugin.api.runtime.PlanExecutionService.class);
        }

        @Bean
        ActiveSessionPointerService activeSessionPointerService() {
            return mock(ActiveSessionPointerService.class);
        }
    }
}
