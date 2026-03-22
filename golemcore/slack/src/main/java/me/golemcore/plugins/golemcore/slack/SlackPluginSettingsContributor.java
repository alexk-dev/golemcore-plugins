package me.golemcore.plugins.golemcore.slack;

import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsAction;
import me.golemcore.plugin.api.extension.spi.PluginSettingsBlock;
import me.golemcore.plugin.api.extension.spi.PluginSettingsCatalogItem;
import me.golemcore.plugin.api.extension.spi.PluginSettingsContributor;
import me.golemcore.plugin.api.extension.spi.PluginSettingsField;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SlackPluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";
    private static final String ACTION_RESTART = "restart-slack";

    private final SlackPluginConfigService configService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String getPluginId() {
        return SlackPluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(SlackPluginConfigService.PLUGIN_ID)
                .pluginName("slack")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Slack")
                .description("Socket Mode credentials, thread routing, and interactive approval flows.")
                .blockKey("channels")
                .blockTitle("Channels")
                .blockDescription("Messaging channel integrations and delivery settings")
                .order(24)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        SlackPluginConfig config = configService.getConfig();

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("botToken", "");
        values.put("appToken", "");
        values.put("replyInThread", Boolean.TRUE.equals(config.getReplyInThread()));

        return PluginSettingsSection.builder()
                .title("Slack")
                .description(
                        "Connect Slack through Socket Mode. Mentions, thread follow-ups, confirmations, and plan approvals stay inside Slack.")
                .fields(List.of(
                        PluginSettingsField.builder()
                                .key("enabled")
                                .type("boolean")
                                .label("Enable Slack")
                                .description("Start the Slack Socket Mode listener when both tokens are configured.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("botToken")
                                .type("secret")
                                .label("Bot Token")
                                .description(
                                        "Slack bot token starting with xoxb-. Leave blank to keep the current secret.")
                                .placeholder("xoxb-...")
                                .build(),
                        PluginSettingsField.builder()
                                .key("appToken")
                                .type("secret")
                                .label("App Token")
                                .description(
                                        "Slack app-level token for Socket Mode, starting with xapp- and granted connections:write.")
                                .placeholder("xapp-...")
                                .build(),
                        PluginSettingsField.builder()
                                .key("replyInThread")
                                .type("boolean")
                                .label("Reply In Threads")
                                .description(
                                        "Route channel mentions into one session per root thread and answer in that thread.")
                                .build()))
                .values(values)
                .blocks(List.of(PluginSettingsBlock.builder()
                        .type("notice")
                        .key("slack-scopes")
                        .title("Required Slack setup")
                        .variant(config.isConfigured() ? "secondary" : "info")
                        .text("Enable Socket Mode and Interactivity. The app token must be an xapp app-level token with connections:write. Subscribe to app_mention, message.channels, message.groups, message.im, and message.mpim. Required bot scopes: app_mentions:read, channels:history, groups:history, im:history, mpim:history, chat:write.")
                        .build()))
                .actions(List.of(PluginSettingsAction.builder()
                        .actionId(ACTION_RESTART)
                        .label("Restart Slack")
                        .variant("secondary")
                        .build()))
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        SlackPluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        config.setReplyInThread(readBoolean(values, "replyInThread", true));

        String botToken = readString(values, "botToken", null);
        if (botToken != null && !botToken.isBlank()) {
            config.setBotToken(botToken);
        }

        String appToken = readString(values, "appToken", null);
        if (appToken != null && !appToken.isBlank()) {
            config.setAppToken(appToken);
        }

        configService.save(config);
        eventPublisher.publishEvent(new SlackRestartEvent());
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        if (!ACTION_RESTART.equals(actionId)) {
            throw new IllegalArgumentException("Unknown Slack action: " + actionId);
        }
        eventPublisher.publishEvent(new SlackRestartEvent());
        return PluginActionResult.builder()
                .status("ok")
                .message("Slack restart requested.")
                .build();
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown Slack settings section: " + sectionKey);
        }
    }

    private boolean readBoolean(Map<String, Object> values, String key, boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private String readString(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        return value instanceof String text ? text : defaultValue;
    }
}
