package me.golemcore.plugins.golemcore.s3;

import me.golemcore.plugin.api.extension.spi.PluginDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S3PluginBootstrapTest {

    @Test
    void shouldDescribeS3Plugin() {
        S3PluginBootstrap bootstrap = new S3PluginBootstrap();

        PluginDescriptor descriptor = bootstrap.descriptor();

        assertEquals("golemcore/s3", descriptor.getId());
        assertEquals("golemcore", descriptor.getProvider());
        assertEquals("s3", descriptor.getName());
        assertEquals(S3PluginConfiguration.class, bootstrap.configurationClass());
    }
}
