package me.golemcore.plugins.golemcore.s3.support;

import java.time.ZonedDateTime;

public record S3ObjectContent(String bucket,String key,byte[]bytes,String eTag,String contentType,ZonedDateTime lastModified){}
