package me.golemcore.plugins.golemcore.notion.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.golemcore.plugins.golemcore.notion.NotionPluginConfig;
import org.junit.jupiter.api.Test;

class NotionReindexScheduleResolverTest {

    private final NotionReindexScheduleResolver resolver = new NotionReindexScheduleResolver();

    @Test
    void shouldResolveFriendlyPresetToCronExpression() {
        NotionPluginConfig config = NotionPluginConfig.builder()
                .localIndexEnabled(true)
                .reindexSchedulePreset("daily")
                .build();

        assertEquals("0 0 3 * * *", resolver.resolveCronExpression(config).orElseThrow());
    }

    @Test
    void shouldUseValidatedCustomCronExpression() {
        NotionPluginConfig config = NotionPluginConfig.builder()
                .localIndexEnabled(true)
                .reindexSchedulePreset("custom")
                .reindexCronExpression("0 15 * * * *")
                .build();

        assertEquals("0 15 * * * *", resolver.resolveCronExpression(config).orElseThrow());
    }

    @Test
    void shouldReturnEmptyWhenIndexingIsDisabledOrExpressionInvalid() {
        assertTrue(resolver.resolveCronExpression(NotionPluginConfig.builder()
                .localIndexEnabled(false)
                .reindexSchedulePreset("hourly")
                .build()).isEmpty());

        assertFalse(resolver.resolveCronExpression(NotionPluginConfig.builder()
                .localIndexEnabled(true)
                .reindexSchedulePreset("custom")
                .reindexCronExpression("not-a-cron")
                .build()).isPresent());
    }
}
