package me.golemcore.plugins.golemcore.notion.support;

public record NotionReindexSummary(int pagesIndexed,int chunksIndexed,int documentsSynced){

public NotionReindexSummary(int pagesIndexed,int chunksIndexed){this(pagesIndexed,chunksIndexed,0);}}
