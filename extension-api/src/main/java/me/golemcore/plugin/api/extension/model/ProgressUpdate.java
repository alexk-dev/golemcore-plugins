package me.golemcore.plugin.api.extension.model;

import java.util.Map;

/**
 * High-level progress update rendered by channels during a turn.
 *
 * @param type
 *            progress update kind
 * @param text
 *            user-facing text content
 * @param metadata
 *            optional structured metadata
 */
public record ProgressUpdate(ProgressUpdateType type,String text,Map<String,Object>metadata){

public ProgressUpdate{metadata=metadata!=null?Map.copyOf(metadata):Map.of();text=text!=null?text:"";}}
