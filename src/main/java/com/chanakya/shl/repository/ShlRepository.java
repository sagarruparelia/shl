package com.chanakya.shl.repository;

import com.chanakya.shl.model.document.ShlDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ShlRepository extends ReactiveMongoRepository<ShlDocument, String> {

    Mono<ShlDocument> findByManifestId(String manifestId);

    Flux<ShlDocument> findByActive(boolean active, Pageable pageable);

    Mono<Long> countByActive(boolean active);

    Flux<ShlDocument> findAllBy(Pageable pageable);
}
