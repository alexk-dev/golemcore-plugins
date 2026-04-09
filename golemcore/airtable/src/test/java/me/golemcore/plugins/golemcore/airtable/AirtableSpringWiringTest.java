package me.golemcore.plugins.golemcore.airtable;

import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class AirtableSpringWiringTest {

    @Test
    void shouldCreateAirtableBeansWithoutDefaultConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AirtablePluginConfiguration.class, TestConfig.class);
            context.refresh();

            assertNotNull(context.getBean(AirtableRecordsToolProvider.class));
            assertNotNull(context.getBean(AirtablePluginSettingsContributor.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @SuppressWarnings("PMD.TestClassWithoutTestCases")
    static class TestConfig {

        @Bean
        @Primary
        PluginConfigurationService pluginConfigurationService() {
            return mock(PluginConfigurationService.class);
        }
    }
}
