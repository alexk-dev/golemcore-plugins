package me.golemcore.plugins.golemcore.nextcloud.support;

public record NextcloudResource(String path,String name,boolean directory,Long size,String mimeType,String etag,String lastModified){}
