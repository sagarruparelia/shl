package com.chanakya.shl.service;

import com.chanakya.shl.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {

    private final S3AsyncClient s3AsyncClient;
    private final AppProperties appProperties;

    public String buildPayloadKey(String shlId, String contentId) {
        return appProperties.getS3().getPayloadPrefix() + shlId + "/" + contentId + ".jwe";
    }

    public Mono<Void> uploadPayload(String s3Key, String jweString) {
        return Mono.fromFuture(() -> s3AsyncClient.putObject(
                PutObjectRequest.builder()
                        .bucket(appProperties.getS3().getBucket())
                        .key(s3Key)
                        .contentType("application/jose")
                        .build(),
                AsyncRequestBody.fromBytes(jweString.getBytes(StandardCharsets.UTF_8))
        )).doOnSuccess(r -> log.debug("Uploaded payload to S3: {}", s3Key))
                .then();
    }

    public Mono<String> downloadPayload(String s3Key) {
        return Mono.fromFuture(() -> s3AsyncClient.getObject(
                GetObjectRequest.builder()
                        .bucket(appProperties.getS3().getBucket())
                        .key(s3Key)
                        .build(),
                AsyncResponseTransformer.toBytes()
        )).map(response -> response.asString(StandardCharsets.UTF_8))
                .doOnSuccess(r -> log.debug("Downloaded payload from S3: {}", s3Key));
    }

    public Mono<Void> deletePayloads(String shlId) {
        String prefix = appProperties.getS3().getPayloadPrefix() + shlId + "/";
        return Mono.fromFuture(() -> s3AsyncClient.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(appProperties.getS3().getBucket())
                        .prefix(prefix)
                        .build()
        )).flatMap(listResponse -> {
            if (listResponse.contents().isEmpty()) {
                return Mono.empty();
            }
            var objectIds = listResponse.contents().stream()
                    .map(s3Object -> ObjectIdentifier.builder().key(s3Object.key()).build())
                    .toList();
            return Mono.fromFuture(() -> s3AsyncClient.deleteObjects(
                    DeleteObjectsRequest.builder()
                            .bucket(appProperties.getS3().getBucket())
                            .delete(Delete.builder().objects(objectIds).build())
                            .build()
            ));
        }).doOnSuccess(r -> log.debug("Deleted payloads from S3 for SHL: {}", shlId))
                .then();
    }
}
