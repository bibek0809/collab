package com.example.bibek.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "document_collaborators")
@IdClass(DocumentCollaboratorId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentCollaborator {

    @Id
    @Column(length = 50)
    private String documentId;

    @Id
    @Column(length = 50)
    private String userId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Permission permission;

    @Column(nullable = false)
    private Instant joinedAt;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) joinedAt = Instant.now();
    }

    public enum Permission {
        OWNER, EDITOR, VIEWER
    }
}
