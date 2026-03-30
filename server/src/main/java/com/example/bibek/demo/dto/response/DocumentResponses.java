package com.example.bibek.demo.dto.response;


import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public sealed interface DocumentResponses {

    @Getter
    @Builder
    final class DocumentResponse implements DocumentResponses {
        private String documentId;
        private String title;
        private String ownerId;
        private String content;
        private Map<String, Long> versionVector;
        private List<CollaboratorInfo> collaborators;
        private Instant createdAt;
        private Instant updatedAt;
        private String shareUrl;
    }

    @Getter @Builder
    final class CollaboratorInfo implements DocumentResponses {
        private String userId;
        private String name;
        private String permission;
        private boolean online;
        private Integer cursorPosition;
    }

    @Getter @Builder
    final class ShareResponse implements DocumentResponses {
        private String shareId;
        private String documentId;
        private String userId;
        private String permission;
        private Instant sharedAt;
    }

    @Getter @Builder
    final class VersionInfo implements DocumentResponses {
        private String versionId;
        private String content;
        private Map<String, Long> versionVector;
        private String createdBy;
        private Instant createdAt;
    }

    @Getter @Builder
    final class VersionHistoryResponse implements DocumentResponses {
        private List<VersionInfo> versions;
        private int totalVersions;
    }

    @Getter @Builder
    final class HealthResponse implements DocumentResponses {
        private String status;
        private Map<String, String> checks;
    }
}
