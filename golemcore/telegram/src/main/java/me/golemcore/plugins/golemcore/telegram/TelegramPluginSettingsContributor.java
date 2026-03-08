package me.golemcore.plugins.golemcore.telegram;

import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.runtime.model.RuntimeConfig;
import me.golemcore.plugin.api.runtime.model.Secret;
import me.golemcore.plugin.api.extension.model.TelegramRestartEvent;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsAction;
import me.golemcore.plugin.api.extension.spi.PluginSettingsBlock;
import me.golemcore.plugin.api.extension.spi.PluginSettingsCatalogItem;
import me.golemcore.plugin.api.extension.spi.PluginSettingsContributor;
import me.golemcore.plugin.api.extension.spi.PluginSettingsField;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugin.api.extension.spi.PluginSettingsTableColumn;
import me.golemcore.plugin.api.extension.spi.PluginSettingsTableRow;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TelegramPluginSettingsContributor implements PluginSettingsContributor {

    private static final String PLUGIN_ID = "golemcore/telegram";
    private static final String SECTION_KEY = "main";
    private static final String ACTION_GENERATE_INVITE = "generate-invite";
    private static final String ACTION_RESTART_TELEGRAM = "restart-telegram";
    private static final String ACTION_REVOKE_INVITE = "revoke-invite";
    private static final String ACTION_REMOVE_ALLOWED_USER = "remove-allowed-user";

    private final RuntimeConfigService runtimeConfigService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(PLUGIN_ID)
                .pluginName("telegram")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Telegram")
                .description("Bot token, invite onboarding, invited user access, and Telegram voice behavior.")
                .blockKey("core")
                .blockTitle("Core")
                .blockDescription("Main runtime settings and access configuration")
                .order(20)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        RuntimeConfig config = runtimeConfigService.getRuntimeConfigForApi();
        RuntimeConfig.TelegramConfig telegram = config.getTelegram();
        RuntimeConfig.VoiceConfig voice = config.getVoice();
        return PluginSettingsSection.builder()
                .title("Telegram")
                .description(
                        "Configure the Telegram channel and finish invite-based onboarding from the plugin itself.")
                .fields(buildFields())
                .values(buildValues(telegram, voice))
                .blocks(buildBlocks(telegram))
                .actions(buildActions(telegram))
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        RuntimeConfig config = runtimeConfigService.getRuntimeConfig();
        RuntimeConfig.TelegramConfig telegram = config.getTelegram();
        RuntimeConfig.VoiceConfig voice = config.getVoice();

        telegram.setEnabled(readBoolean(values, "enabled"));
        telegram.setAuthMode("invite_only");

        String token = readString(values, "token");
        if (token != null && !token.isBlank()) {
            telegram.setToken(Secret.of(token));
        }

        voice.setTelegramRespondWithVoice(readBoolean(values, "telegramRespondWithVoice"));
        voice.setTelegramTranscribeIncoming(readBoolean(values, "telegramTranscribeIncoming"));

        runtimeConfigService.updateRuntimeConfig(config);
        eventPublisher.publishEvent(new TelegramRestartEvent());
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        return switch (actionId) {
        case ACTION_GENERATE_INVITE -> generateInvite();
        case ACTION_RESTART_TELEGRAM -> {
            eventPublisher.publishEvent(new TelegramRestartEvent());
            yield PluginActionResult.builder()
                    .status("ok")
                    .message("Telegram restart requested.")
                    .build();
        }
        case ACTION_REVOKE_INVITE -> revokeInvite(payload);
        case ACTION_REMOVE_ALLOWED_USER -> removeAllowedUser(payload);
        default -> throw new IllegalArgumentException("Unknown Telegram plugin action: " + actionId);
        };
    }

    private List<PluginSettingsField> buildFields() {
        return List.of(
                PluginSettingsField.builder()
                        .key("enabled")
                        .type("boolean")
                        .label("Enable Telegram")
                        .description("Start Telegram long polling when a valid bot token is configured.")
                        .build(),
                PluginSettingsField.builder()
                        .key("token")
                        .type("secret")
                        .label("Bot Token")
                        .description("Telegram Bot API token from @BotFather. Leave blank to keep the current secret.")
                        .placeholder("123456:ABC-DEF...")
                        .build(),
                PluginSettingsField.builder()
                        .key("telegramRespondWithVoice")
                        .type("boolean")
                        .label("Respond With Voice")
                        .description("Allow Telegram responses to include generated voice messages when supported.")
                        .build(),
                PluginSettingsField.builder()
                        .key("telegramTranscribeIncoming")
                        .type("boolean")
                        .label("Transcribe Incoming Voice")
                        .description(
                                "Automatically transcribe incoming Telegram voice messages before sending them into the agent loop.")
                        .build());
    }

    private Map<String, Object> buildValues(
            RuntimeConfig.TelegramConfig telegram,
            RuntimeConfig.VoiceConfig voice) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(telegram.getEnabled()));
        values.put("token", "");
        values.put("telegramRespondWithVoice", Boolean.TRUE.equals(voice.getTelegramRespondWithVoice()));
        values.put("telegramTranscribeIncoming", Boolean.TRUE.equals(voice.getTelegramTranscribeIncoming()));
        return values;
    }

    private List<PluginSettingsBlock> buildBlocks(RuntimeConfig.TelegramConfig telegram) {
        List<String> allowedUsers = telegram.getAllowedUsers() != null ? telegram.getAllowedUsers() : List.of();
        List<RuntimeConfig.InviteCode> inviteCodes = telegram.getInviteCodes() != null ? telegram.getInviteCodes()
                : List.of();

        List<PluginSettingsBlock> blocks = new ArrayList<>();
        blocks.add(PluginSettingsBlock.builder()
                .type("notice")
                .key("telegram-onboarding")
                .title(allowedUsers.isEmpty() ? "No invited user yet" : "Invited user connected")
                .variant(allowedUsers.isEmpty() ? "info" : "secondary")
                .text(allowedUsers.isEmpty()
                        ? "Generate an invite code and redeem it from Telegram to claim access."
                        : "A Telegram user is already bound to this installation. Remove that user before issuing a replacement invite.")
                .build());

        if (!inviteCodes.isEmpty()) {
            List<PluginSettingsTableRow> rows = new ArrayList<>();
            for (RuntimeConfig.InviteCode inviteCode : inviteCodes) {
                Map<String, Object> cells = new LinkedHashMap<>();
                cells.put("code", inviteCode.getCode());
                cells.put("status", inviteCode.isUsed() ? "Used" : "Active");
                cells.put("createdAt", inviteCode.getCreatedAt() != null ? inviteCode.getCreatedAt().toString() : "");
                List<PluginSettingsAction> actions = inviteCode.isUsed()
                        ? List.of()
                        : List.of(PluginSettingsAction.builder()
                                .actionId(ACTION_REVOKE_INVITE)
                                .label("Revoke")
                                .variant("danger")
                                .confirmationMessage("Revoke this invite code?")
                                .build());
                rows.add(PluginSettingsTableRow.builder()
                        .id(inviteCode.getCode())
                        .cells(cells)
                        .actions(actions)
                        .build());
            }
            blocks.add(PluginSettingsBlock.builder()
                    .type("table")
                    .key("invite-codes")
                    .title("Invite Codes")
                    .description("Single-use invite codes that grant Telegram access when redeemed.")
                    .columns(List.of(
                            PluginSettingsTableColumn.builder().key("code").label("Code").build(),
                            PluginSettingsTableColumn.builder().key("status").label("Status").build(),
                            PluginSettingsTableColumn.builder().key("createdAt").label("Created").build()))
                    .rows(rows)
                    .build());
        }

        if (!allowedUsers.isEmpty()) {
            List<PluginSettingsTableRow> rows = allowedUsers.stream()
                    .map(userId -> PluginSettingsTableRow.builder()
                            .id(userId)
                            .cells(Map.of("userId", userId, "status", "Connected"))
                            .actions(List.of(PluginSettingsAction.builder()
                                    .actionId(ACTION_REMOVE_ALLOWED_USER)
                                    .label("Remove")
                                    .variant("danger")
                                    .confirmationMessage(
                                            "Remove this Telegram user and revoke any active invite codes?")
                                    .build()))
                            .build())
                    .toList();
            blocks.add(PluginSettingsBlock.builder()
                    .type("table")
                    .key("allowed-users")
                    .title("Invited Users")
                    .description("Telegram users currently authorized for this installation.")
                    .columns(List.of(
                            PluginSettingsTableColumn.builder().key("userId").label("User ID").build(),
                            PluginSettingsTableColumn.builder().key("status").label("Status").build()))
                    .rows(rows)
                    .build());
        }

        return blocks;
    }

    private List<PluginSettingsAction> buildActions(RuntimeConfig.TelegramConfig telegram) {
        List<String> allowedUsers = telegram.getAllowedUsers() != null ? telegram.getAllowedUsers() : List.of();
        List<PluginSettingsAction> actions = new ArrayList<>();
        if (allowedUsers.isEmpty()) {
            actions.add(PluginSettingsAction.builder()
                    .actionId(ACTION_GENERATE_INVITE)
                    .label("Generate Invite")
                    .variant("primary")
                    .build());
        }
        actions.add(PluginSettingsAction.builder()
                .actionId(ACTION_RESTART_TELEGRAM)
                .label("Restart Telegram")
                .variant("secondary")
                .build());
        return actions;
    }

    private PluginActionResult generateInvite() {
        List<String> allowedUsers = runtimeConfigService.getTelegramAllowedUsers();
        if (!allowedUsers.isEmpty()) {
            return PluginActionResult.builder()
                    .status("conflict")
                    .message(
                            "A Telegram user is already connected. Remove that user before generating a replacement invite.")
                    .build();
        }
        RuntimeConfig.InviteCode inviteCode = runtimeConfigService.generateInviteCode();
        return PluginActionResult.builder()
                .status("ok")
                .message("Invite code generated: " + inviteCode.getCode())
                .build();
    }

    private PluginActionResult revokeInvite(Map<String, Object> payload) {
        String code = readString(payload, "rowId");
        if (code == null || code.isBlank()) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Invite code is required.")
                    .build();
        }
        boolean revoked = runtimeConfigService.revokeInviteCode(code);
        return PluginActionResult.builder()
                .status(revoked ? "ok" : "missing")
                .message(revoked ? "Invite code revoked." : "Invite code was not found.")
                .build();
    }

    private PluginActionResult removeAllowedUser(Map<String, Object> payload) {
        String userId = readString(payload, "rowId");
        if (userId == null || userId.isBlank()) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Telegram user id is required.")
                    .build();
        }
        boolean removed = runtimeConfigService.removeTelegramAllowedUser(userId);
        return PluginActionResult.builder()
                .status(removed ? "ok" : "missing")
                .message(removed ? "Telegram user removed." : "Telegram user was not found.")
                .build();
    }

    private Boolean readBoolean(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return false;
    }

    private String readString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown Telegram settings section: " + sectionKey);
        }
    }
}
