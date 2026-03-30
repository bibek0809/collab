package com.example.bibek.demo.model;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_owner_id", columnList = "ownerId"),
        @Index(name = "idx_updated_at", columnList = "updatedAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @Column(length = 50)
    private String documentId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 50)
    private String ownerId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 50)
    private String homeRegion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "text")
    @Builder.Default
    private Map<String, Long> versionVector = new HashMap<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

