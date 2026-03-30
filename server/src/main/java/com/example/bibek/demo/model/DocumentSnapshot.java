package com.example.bibek.demo.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "document_snapshots", indexes = {
        @Index(name = "idx_snapshot_doc_created", columnList = "documentId, createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentSnapshot {

    @Id
    @Column(length = 50)
    private String snapshotId;

    @Column(nullable = false, length = 50)
    private String documentId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "text")
    private Map<String, Long> versionVector;

    @Column(nullable = false)
    private Integer operationCount;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
