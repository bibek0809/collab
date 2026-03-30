package com.example.bibek.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

public sealed interface DocumentRequests {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    final class CreateDocumentRequest implements DocumentRequests {
        @NotBlank
        @Size(max = 255)
        private String title;
        private String content;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    final class UpdateTitleRequest implements DocumentRequests {
        @NotBlank @Size(max = 255)
        private String title;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    final class ShareDocumentRequest implements DocumentRequests {
        @NotBlank
        private String userId;
        @NotBlank
        private String permission; // EDITOR or VIEWER
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    final class RestoreVersionRequest implements DocumentRequests {
        @NotBlank
        private String versionId;
    }
}

