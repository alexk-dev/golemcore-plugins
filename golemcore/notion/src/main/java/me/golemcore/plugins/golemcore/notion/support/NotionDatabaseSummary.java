package me.golemcore.plugins.golemcore.notion.support;

import java.util.List;
import java.util.Map;

public record NotionDatabaseSummary(String id,String title,String url,List<Map<String,Object>>dataSources){}
