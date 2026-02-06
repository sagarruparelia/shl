package com.chanakya.shl.controller;

import com.chanakya.shl.model.dto.request.CreateShlRequest;
import com.chanakya.shl.model.dto.response.CreateShlResponse;
import com.chanakya.shl.model.dto.response.ShlDetailResponse;
import com.chanakya.shl.service.AccessLogService;
import com.chanakya.shl.service.ShlService;
import tools.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/shl")
@RequiredArgsConstructor
@Slf4j
public class ShlManagementController {

    private final ShlService shlService;
    private final AccessLogService accessLogService;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<CreateShlResponse>> createFromJson(@Valid @RequestBody CreateShlRequest request) {
        log.debug("Creating SHL from JSON content");
        return shlService.createFromJson(request)
                .map(response -> ResponseEntity.status(201).body(response));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<CreateShlResponse>> createFromFile(
            @RequestPart("file") FilePart filePart,
            @RequestPart(value = "options", required = false) Part optionsPart) {

        log.debug("Creating SHL from file upload: {}", filePart.filename());

        Mono<CreateShlOptions> optionsMono;
        if (optionsPart != null) {
            optionsMono = DataBufferUtils.join(optionsPart.content())
                    .map(dataBuffer -> {
                        try {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            return bytes;
                        } finally {
                            DataBufferUtils.release(dataBuffer);
                        }
                    })
                    .flatMap(bytes -> {
                        try {
                            return Mono.just(objectMapper.readValue(bytes, CreateShlOptions.class));
                        } catch (Exception e) {
                            return Mono.just(new CreateShlOptions());
                        }
                    });
        } else {
            optionsMono = Mono.just(new CreateShlOptions());
        }

        return optionsMono.flatMap(options ->
                DataBufferUtils.join(filePart.content())
                        .map(dataBuffer -> {
                            try {
                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(bytes);
                                return bytes;
                            } finally {
                                DataBufferUtils.release(dataBuffer);
                            }
                        })
                        .flatMap(fileBytes -> {
                            String contentType = filePart.headers().getContentType() != null
                                    ? filePart.headers().getContentType().toString()
                                    : "application/octet-stream";
                            return shlService.createFromFile(
                                    fileBytes, contentType, filePart.filename(),
                                    options.label, options.passcode, options.expirationInSeconds,
                                    options.singleUse, options.longTerm);
                        })
        ).map(response -> ResponseEntity.status(201).body(response));
    }

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> listShls(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size);
        return Mono.zip(
                shlService.listShls(active, pageable).collectList(),
                shlService.countShls(active)
        ).map(tuple -> ResponseEntity.ok(Map.of(
                "content", tuple.getT1(),
                "totalElements", tuple.getT2(),
                "page", page,
                "size", size
        )));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ShlDetailResponse>> getShlDetail(@PathVariable String id) {
        return shlService.getShlDetail(id)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deactivate(@PathVariable String id) {
        return shlService.deactivate(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @GetMapping("/{id}/access-log")
    public Mono<ResponseEntity<Map<String, Object>>> getAccessLog(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, size);
        return accessLogService.getAccessLogs(id, pageable)
                .collectList()
                .map(logs -> ResponseEntity.ok(Map.<String, Object>of(
                        "content", logs,
                        "page", page,
                        "size", size
                )));
    }

    private static class CreateShlOptions {
        public String label;
        public String passcode;
        public Long expirationInSeconds;
        public boolean singleUse;
        public boolean longTerm;
    }
}
