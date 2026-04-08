package me.golemcore.plugins.golemcore.nextcloud;

import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudWebDavClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class NextcloudSpringWiringTest {

    @Test
    void shouldCreateNextcloudBeansWithoutDefaultConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(NextcloudPluginConfiguration.class, TestConfig.class);
            context.refresh();

            assertNotNull(context.getBean(NextcloudWebDavClient.class));
            assertNotNull(context.getBean(NextcloudPluginSettingsContributor.class));
            assertNotNull(context.getBean(NextcloudFilesToolProvider.class));
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
