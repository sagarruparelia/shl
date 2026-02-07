package com.chanakya.shl.controller;

import com.chanakya.shl.model.dto.request.ManifestRequest;
import com.chanakya.shl.model.dto.response.ManifestResponse;
import com.chanakya.shl.service.ManifestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/shl")
@RequiredArgsConstructor
@Slf4j
public class ShlProtocolController {

    private final ManifestService manifestService;

    @PostMapping("/manifest/{manifestId}")
    public Mono<ResponseEntity<ManifestResponse>> manifest(
            @PathVariable String manifestId,
            @Valid @RequestBody ManifestRequest request,
            ServerHttpRequest httpRequest) {

        log.debug("Manifest request for manifestId: {}", manifestId);
        return manifestService.processManifestRequest(manifestId, request, httpRequest)
                .map(response -> {
                    var builder = ResponseEntity.ok();
                    if ("can-change".equals(response.getStatus())) {
                        builder.header("Retry-After", "60");
                    }
                    return builder.body(response);
                });
    }

    @GetMapping("/direct/{manifestId}")
    public Mono<ResponseEntity<ManifestResponse>> directAccess(
            @PathVariable String manifestId,
            @RequestParam String recipient,
            ServerHttpRequest httpRequest) {

        log.debug("Direct access for manifestId: {}", manifestId);
        return manifestService.processDirectAccess(manifestId, recipient, httpRequest)
                .map(response -> {
                    var builder = ResponseEntity.ok();
                    if ("can-change".equals(response.getStatus())) {
                        builder.header("Retry-After", "60");
                    }
                    return builder.body(response);
                });
    }

    @GetMapping("/file/{tokenId}")
    public Mono<ResponseEntity<String>> downloadFile(
            @PathVariable String tokenId,
            ServerHttpRequest httpRequest) {

        log.debug("File download for token: {}", tokenId);
        return manifestService.downloadFile(tokenId, httpRequest)
                .map(jweString -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "application/jose")
                        .body(jweString));
    }
}
