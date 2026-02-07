package com.chanakya.shl.service;

import com.chanakya.shl.util.Base64UrlUtil;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

@Service
@Slf4j
public class EncryptionService {

    public Mono<String> encrypt(String plaintext, String base64UrlKey, String contentType) {
        return Mono.fromCallable(() -> {
            byte[] keyBytes = Base64UrlUtil.decode(base64UrlKey);
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

            JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM)
                    .compressionAlgorithm(CompressionAlgorithm.DEF)
                    .contentType(contentType)
                    .build();

            JWEObject jweObject = new JWEObject(header, new Payload(plaintext));
            jweObject.encrypt(new DirectEncrypter(secretKey));

            return jweObject.serialize();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> decrypt(String jweCompact, String base64UrlKey) {
        return Mono.fromCallable(() -> {
            byte[] keyBytes = Base64UrlUtil.decode(base64UrlKey);
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

            JWEObject jweObject = JWEObject.parse(jweCompact);
            jweObject.decrypt(new DirectDecrypter(secretKey));

            return jweObject.getPayload().toString();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
