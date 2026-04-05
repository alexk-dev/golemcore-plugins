package me.golemcore.plugins.golemcore.notion.support;

import java.util.List;
import java.util.Map;

public record NotionDataSourceQueryResult(String dataSourceId,int count,boolean hasMore,String nextCursor,List<Map<String,Object>>results){}
