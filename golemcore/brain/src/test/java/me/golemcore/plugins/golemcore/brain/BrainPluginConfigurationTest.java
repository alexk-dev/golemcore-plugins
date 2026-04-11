package me.golemcore.plugins.golemcore.brain;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import me.golemcore.plugin.api.runtime.PluginConfigurationService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class BrainPluginConfigurationTest {

    @Test
    void shouldCreateBrainPluginBeans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(BrainPluginConfiguration.class, TestConfig.class);
            context.refresh();

            assertNotNull(context.getBean(BrainToolProvider.class));
            assertNotNull(context.getBean(BrainPluginSettingsContributor.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @SuppressWarnings("PMD.TestClassWithoutTestCases")
    static class TestConfig {

        @Bean
        PluginConfigurationService pluginConfigurationService() {
            return mock(PluginConfigurationService.class);
        }
    }
}
