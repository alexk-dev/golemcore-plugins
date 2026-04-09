package me.golemcore.plugins.golemcore.s3.support;

import java.time.ZonedDateTime;

public record S3ObjectInfo(String bucket,String key,String name,boolean directory,Long size,String eTag,String contentType,ZonedDateTime lastModified){}
