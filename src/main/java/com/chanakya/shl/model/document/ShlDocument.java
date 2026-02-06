package com.chanakya.shl.model.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "shlinks")
public class ShlDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String manifestId;

    private String label;

    private String encryptionKey;

    private String flags;

    private String passcodeHash;

    private Integer passcodeFailuresRemaining;

    private Instant expiresAt;

    @Builder.Default
    private boolean active = true;

    private boolean singleUse;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
