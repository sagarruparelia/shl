package com.chanakya.shl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;

import java.net.URI;

@Configuration
public class AwsConfig {

    @Bean
    public DefaultCredentialsProvider defaultCredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    public S3AsyncClient s3AsyncClient(AppProperties appProperties,
                                       DefaultCredentialsProvider defaultCredentialsProvider) {
        S3AsyncClientBuilder builder = S3AsyncClient.builder()
                .region(Region.of(appProperties.getS3().getRegion()))
                .httpClientBuilder(NettyNioAsyncHttpClient.builder());

        String endpoint = appProperties.getS3().getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")));
        } else {
            builder.credentialsProvider(defaultCredentialsProvider);
        }

        return builder.build();
    }
}
