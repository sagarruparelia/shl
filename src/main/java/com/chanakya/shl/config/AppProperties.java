package com.chanakya.shl.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl;
    private String viewerPath;
    private int defaultPasscodeAttempts = 10;
    private int fileTokenTtlMinutes = 60;
    private int qrCodeDefaultSize = 300;
    private S3Properties s3 = new S3Properties();
    private HealthLakeProperties healthlake = new HealthLakeProperties();

    @Getter
    @Setter
    public static class S3Properties {
        private String bucket;
        private String region;
        private String endpoint;
        private String payloadPrefix = "payloads/";
    }

    @Getter
    @Setter
    public static class HealthLakeProperties {
        private String datastoreEndpoint;
        private String region = "us-east-1";
    }
}
