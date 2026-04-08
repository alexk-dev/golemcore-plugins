package me.golemcore.plugins.golemcore.nextcloud.support;

public record NextcloudFileContent(String path,byte[]bytes,String mimeType,Long size,String etag,String lastModified){}
