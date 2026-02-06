package com.chanakya.shl.repository;

import com.chanakya.shl.model.document.AccessLogDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AccessLogRepository extends ReactiveMongoRepository<AccessLogDocument, String> {

    Flux<AccessLogDocument> findByShlIdOrderByCreatedAtDesc(String shlId, Pageable pageable);

    Mono<Long> countByShlId(String shlId);

    Mono<Long> countByShlIdAndSuccess(String shlId, boolean success);
}
