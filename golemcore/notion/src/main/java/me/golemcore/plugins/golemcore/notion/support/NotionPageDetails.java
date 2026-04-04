package me.golemcore.plugins.golemcore.notion.support;

import java.util.List;
import java.util.Map;

public record NotionPageDetails(String id,String title,String url,List<NotionFileAttachmentSummary>files,Map<String,Object>rawProperties){}
