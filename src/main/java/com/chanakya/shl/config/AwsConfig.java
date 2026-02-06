package com.chanakya.shl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Configuration
public class AwsConfig {

    @Bean
    public S3AsyncClient s3AsyncClient(AppProperties appProperties) {
        return S3AsyncClient.builder()
                .region(Region.of(appProperties.getS3().getRegion()))
                .httpClientBuilder(NettyNioAsyncHttpClient.builder())
                .build();
    }
}
