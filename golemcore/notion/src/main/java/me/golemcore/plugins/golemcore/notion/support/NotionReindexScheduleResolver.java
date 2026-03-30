package me.golemcore.plugins.golemcore.notion.support;

import me.golemcore.plugins.golemcore.notion.NotionPluginConfig;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NotionReindexScheduleResolver {

    public Optional<String> resolveCronExpression(NotionPluginConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getLocalIndexEnabled())) {
            return Optional.empty();
        }
        String preset = config.getReindexSchedulePreset();
        if (preset == null) {
            return Optional.empty();
        }
        String expression = switch (preset) {
        case "hourly" -> "0 0 * * * *";
        case "every_6_hours" -> "0 0 */6 * * *";
        case "daily" -> "0 0 3 * * *";
        case "weekly" -> "0 0 3 * * MON";
        case "custom" -> config.getReindexCronExpression();
        case "disabled" -> "";
        default -> "";
        };
        if (expression == null || expression.isBlank() || !CronExpression.isValidExpression(expression)) {
            return Optional.empty();
        }
        return Optional.of(expression);
    }
}
