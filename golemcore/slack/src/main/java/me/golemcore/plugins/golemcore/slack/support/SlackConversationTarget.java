package me.golemcore.plugins.golemcore.slack.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record SlackConversationTarget(String channelId,String threadTs,String transportChatId,String conversationKey){

private static final String THREAD_SEPARATOR="::";private static final String CONVERSATION_PREFIX="slk_";private static final int CONVERSATION_HASH_LENGTH=28;

public static SlackConversationTarget direct(String channelId){String normalizedChannelId=requireValue(channelId,"channelId");return new SlackConversationTarget(normalizedChannelId,null,normalizedChannelId,hashConversationKey("dm:"+normalizedChannelId));}

public static SlackConversationTarget thread(String channelId,String rootThreadTs){String normalizedChannelId=requireValue(channelId,"channelId");String normalizedThreadTs=requireValue(rootThreadTs,"rootThreadTs");String transportChatId=normalizedChannelId+THREAD_SEPARATOR+normalizedThreadTs;return new SlackConversationTarget(normalizedChannelId,normalizedThreadTs,transportChatId,hashConversationKey("thread:"+transportChatId));}

public static SlackConversationTarget fromTransportChatId(String transportChatId){String normalized=requireValue(transportChatId,"transportChatId");int separatorIndex=normalized.indexOf(THREAD_SEPARATOR);if(separatorIndex<0){return direct(normalized);}String channelId=normalized.substring(0,separatorIndex);String threadTs=normalized.substring(separatorIndex+THREAD_SEPARATOR.length());return thread(channelId,threadTs);}

public boolean threaded(){return threadTs!=null&&!threadTs.isBlank();}

private static String requireValue(String value,String label){if(value==null){throw new IllegalArgumentException(label+" must not be null");}String normalized=value.trim();if(normalized.isBlank()){throw new IllegalArgumentException(label+" must not be blank");}return normalized;}

private static String hashConversationKey(String seed){try{MessageDigest digest=MessageDigest.getInstance("SHA-256");String hex=HexFormat.of().formatHex(digest.digest(seed.getBytes(StandardCharsets.UTF_8)));return CONVERSATION_PREFIX+hex.substring(0,CONVERSATION_HASH_LENGTH);}catch(NoSuchAlgorithmException ex){throw new IllegalStateException("SHA-256 is not available",ex);}}}
