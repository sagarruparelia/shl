package com.chanakya.shl.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrCodeService {

    private final S3StorageService s3StorageService;

    public Mono<byte[]> getOrGenerateQrCode(String shlId, String shlinkUrl, int size) {
        return s3StorageService.getQrCode(shlId, size)
                .switchIfEmpty(
                        generateQrCode(shlinkUrl, size)
                                .flatMap(pngBytes -> s3StorageService.uploadQrCode(shlId, size, pngBytes)
                                        .thenReturn(pngBytes))
                );
    }

    private Mono<byte[]> generateQrCode(String content, int size) {
        return Mono.fromCallable(() -> {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2
            );
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
