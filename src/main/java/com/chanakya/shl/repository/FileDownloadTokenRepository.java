package com.chanakya.shl.repository;

import com.chanakya.shl.model.document.FileDownloadToken;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface FileDownloadTokenRepository extends ReactiveMongoRepository<FileDownloadToken, String> {

    Mono<FileDownloadToken> findByIdAndConsumedFalse(String id);
}
