package me.golemcore.plugins.golemcore.obsidian.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown=true)public record ObsidianSearchResult(String filename,double score,List<Match>matches){

public ObsidianSearchResult{matches=matches==null?List.of():List.copyOf(matches);}

@JsonIgnoreProperties(ignoreUnknown=true)public record Match(String context,Integer start,Integer end){}}
