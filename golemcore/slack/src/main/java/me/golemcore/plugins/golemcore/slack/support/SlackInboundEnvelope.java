package me.golemcore.plugins.golemcore.slack.support;

public record SlackInboundEnvelope(String channelId,String channelType,String userId,String text,String eventTs,String rootThreadTs,boolean directMessage,boolean explicitMention){}
