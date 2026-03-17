package me.golemcore.plugins.golemcore.slack.support;

public record SlackActionEnvelope(String actionId,String actionValue,String channelId,String userId,String messageTs,String threadTs){

public String transportChatId(){return threadTs!=null&&!threadTs.isBlank()?channelId+"::"+threadTs:channelId;}}
