package com.chanakya.shl.model.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "file_download_tokens")
public class FileDownloadToken {

    @Id
    private String id;

    private String contentId;

    private String shlId;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    @Builder.Default
    private boolean consumed = false;
}
