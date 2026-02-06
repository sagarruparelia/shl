package com.chanakya.shl.repository;

import com.chanakya.shl.model.document.ShlContentDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ShlContentRepository extends ReactiveMongoRepository<ShlContentDocument, String> {

    Flux<ShlContentDocument> findByShlId(String shlId);

    Mono<Long> countByShlId(String shlId);
}
