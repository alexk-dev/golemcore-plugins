package me.golemcore.plugins.golemcore.notion.support;

import java.util.Map;

public record NotionDataSourceSummary(String id,String title,String url,Map<String,Object>properties){}
