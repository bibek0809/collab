package com.example.bibek.demo.controller;

import com.example.bibek.demo.dto.request.DocumentRequests;
import com.example.bibek.demo.dto.response.DocumentResponses;
import com.example.bibek.demo.service.DocumentService;
import com.example.bibek.demo.service.VersionHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DocumentController {

    private final DocumentService documentService;
    private final VersionHistoryService versionHistoryService;

    // ─── Document CRUD ───

    @PostMapping
    public ResponseEntity<DocumentResponses.DocumentResponse> createDocument(
            @RequestHeader(value = "User-Id", defaultValue = "anonymous") String userId,
            @Valid @RequestBody DocumentRequests.CreateDocumentRequest request) {
        DocumentResponses.DocumentResponse response = documentService.createDocument(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponses.DocumentResponse> getDocument(
            @PathVariable String documentId,
            @RequestHeader(value = "User-Id", defaultValue = "anonymous") String userId) {
        DocumentResponses.DocumentResponse response = documentService.getDocument(documentId, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{documentId}/title")
    public ResponseEntity<DocumentResponses.DocumentResponse> updateTitle(
            @PathVariable String documentId,
            @RequestHeader(value = "User-Id", defaultValue = "anonymous") String userId,
            @Valid @RequestBody DocumentRequests.UpdateTitleRequest request) {
        DocumentResponses.DocumentResponse response = documentService.updateTitle(documentId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable String documentId,
            @RequestHeader(value = "User-Id", defaultValue = "anonymous") String userId) {
        documentService.deleteDocument(documentId, userId);
        return ResponseEntity.noContent().build();
    }

    // ─── Sharing ───

    @PostMapping("/{documentId}/share")
    public ResponseEntity<DocumentResponses.ShareResponse> shareDocument(
            @PathVariable String documentId,
            @RequestHeader(value = "User-Id", defaultValue = "anonymous") String userId,
            @Valid @RequestBody DocumentRequests.ShareDocumentRequest request) {
        DocumentResponses.ShareResponse response = documentService.shareDocument(documentId, userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{documentId}/collaborators")
    public ResponseEntity<List<DocumentResponses.CollaboratorInfo>> getCollaborators(
            @PathVariable String documentId) {
        List<DocumentResponses.CollaboratorInfo> collaborators = documentService.getCollaborators(documentId);
        return ResponseEntity.ok(collaborators);
    }

    // ─── Version History ───

    @GetMapping("/{documentId}/history")
    public ResponseEntity<DocumentResponses.VersionHistoryResponse> getHistory(
            @PathVariable String documentId,
            @RequestParam(defaultValue = "50") int limit) {
        DocumentResponses.VersionHistoryResponse response = versionHistoryService.getHistory(documentId, limit);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{documentId}/restore")
    public ResponseEntity<Void> restoreVersion(
            @PathVariable String documentId,
            @Valid @RequestBody DocumentRequests.RestoreVersionRequest request) {
        versionHistoryService.restoreVersion(documentId, request.getVersionId());
        return ResponseEntity.ok().build();
    }
}
