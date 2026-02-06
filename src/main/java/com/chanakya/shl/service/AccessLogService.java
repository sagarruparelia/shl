package com.chanakya.shl.service;

import com.chanakya.shl.model.document.AccessLogDocument;
import com.chanakya.shl.model.dto.response.AccessLogEntry;
import com.chanakya.shl.model.enums.AccessAction;
import com.chanakya.shl.repository.AccessLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessLogService {

    private final AccessLogRepository accessLogRepository;

    public Mono<AccessLogDocument> logAccess(String shlId, AccessAction action, String recipient,
                                              ServerHttpRequest request, boolean success, String failureReason) {
        String ipAddress = extractIpAddress(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");

        AccessLogDocument logDoc = AccessLogDocument.builder()
                .shlId(shlId)
                .action(action.name())
                .recipient(recipient)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(success)
                .failureReason(failureReason)
                .build();

        return accessLogRepository.save(logDoc)
                .doOnSuccess(saved -> log.debug("Access logged: {} for SHL {} - success: {}", action, shlId, success));
    }

    public Flux<AccessLogEntry> getAccessLogs(String shlId, Pageable pageable) {
        return accessLogRepository.findByShlIdOrderByCreatedAtDesc(shlId, pageable)
                .map(this::toEntry);
    }

    public Mono<Long> getAccessCount(String shlId) {
        return accessLogRepository.countByShlIdAndSuccess(shlId, true);
    }

    private AccessLogEntry toEntry(AccessLogDocument doc) {
        return AccessLogEntry.builder()
                .id(doc.getId())
                .action(doc.getAction())
                .recipient(doc.getRecipient())
                .ipAddress(doc.getIpAddress())
                .userAgent(doc.getUserAgent())
                .success(doc.isSuccess())
                .failureReason(doc.getFailureReason())
                .createdAt(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
                .build();
    }

    private String extractIpAddress(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
