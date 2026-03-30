package me.golemcore.plugins.golemcore.telegram.adapter.inbound.telegram;

import me.golemcore.plugin.api.extension.model.Message;

public record TelegramInboundFragment(String aggregationKey,Message message){}
