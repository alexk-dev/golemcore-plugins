package me.golemcore.plugins.golemcore.s3;

import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import me.golemcore.plugins.golemcore.s3.support.S3MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class S3SpringWiringTest {

    @Test
    void shouldCreateS3BeansWithoutDefaultConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(S3PluginConfiguration.class, TestConfig.class);
            context.refresh();

            assertNotNull(context.getBean(S3MinioClient.class));
            assertNotNull(context.getBean(S3PluginSettingsContributor.class));
            assertNotNull(context.getBean(S3FilesToolProvider.class));
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
