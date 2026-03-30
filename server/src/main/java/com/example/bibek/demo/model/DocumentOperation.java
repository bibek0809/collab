package com.example.bibek.demo.model;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "document_operations", indexes = {
        @Index(name = "idx_doc_site", columnList = "documentId, siteId, logicalTimestamp"),
        @Index(name = "idx_doc_created", columnList = "documentId, createdAt")
}, uniqueConstraints = {
        @UniqueConstraint(name = "unique_char_id", columnNames = {"documentId", "characterId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long operationId;

    @Column(nullable = false, length = 50)
    private String documentId;

    @Column(nullable = false, length = 50)
    private String siteId;

    @Column(nullable = false)
    private Long logicalTimestamp;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OperationType operationType;

    @Column(nullable = false, length = 100)
    private String characterId;

    @Column(length = 1)
    private String characterValue;

    @Column(length = 100)
    private String previousId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "text")
    private Map<String, Object> formatData;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public enum OperationType {
        INSERT, DELETE, FORMAT
    }
}
