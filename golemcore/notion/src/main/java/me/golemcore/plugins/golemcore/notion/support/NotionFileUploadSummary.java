package me.golemcore.plugins.golemcore.notion.support;

public record NotionFileUploadSummary(String id,String status,String filename,String contentType,Long contentLength,String uploadUrl,String expiryTime){}
