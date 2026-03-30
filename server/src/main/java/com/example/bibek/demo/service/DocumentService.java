package com.example.bibek.demo.service;

import com.example.bibek.demo.crdt.CrdtChar;
import com.example.bibek.demo.crdt.CrdtDocument;
import com.example.bibek.demo.dto.request.DocumentRequests;
import com.example.bibek.demo.dto.response.DocumentResponses;
import com.example.bibek.demo.exception.DocumentNotFoundException;
import com.example.bibek.demo.model.Document;
import com.example.bibek.demo.model.DocumentCollaborator;
import com.example.bibek.demo.repo.DocumentCollaboratorRepository;
import com.example.bibek.demo.repo.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentCollaboratorRepository collaboratorRepository;
    private final CrdtService crdtService;
    private final PresenceService presenceService;

    @Value("${collab.region:us-east}")
    private String currentRegion;

    @Transactional
    public DocumentResponses.DocumentResponse createDocument(String userId, DocumentRequests.CreateDocumentRequest request) {
        String docId = "doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Document doc = Document.builder()
                .documentId(docId)
                .title(request.getTitle())
                .ownerId(userId)
                .homeRegion(currentRegion)
                .versionVector(new HashMap<>())
                .build();
        documentRepository.save(doc);

        // Add owner as collaborator
        DocumentCollaborator collab = DocumentCollaborator.builder()
                .documentId(docId)
                .userId(userId)
                .permission(DocumentCollaborator.Permission.OWNER)
                .build();
        collaboratorRepository.save(collab);

        // Initialize CRDT document and insert initial content
        CrdtDocument crdtDoc = crdtService.getOrCreateDocument(docId);
        if (request.getContent() != null && !request.getContent().isEmpty()) {
            String siteId = "server";
            String prevId = null;
            for (char c : request.getContent().toCharArray()) {
                CrdtChar inserted = crdtDoc.localInsert(c, prevId, siteId);
                prevId = inserted.getId();
            }
            crdtService.persistOperations(docId, crdtDoc);
        }

        log.info("Document created: {} by user {}", docId, userId);

        return DocumentResponses.DocumentResponse.builder()
                .documentId(docId)
                .title(doc.getTitle())
                .ownerId(userId)
                .content(request.getContent() != null ? request.getContent() : "")
                .versionVector(doc.getVersionVector())
                .collaborators(List.of())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .shareUrl("https://docs.example.com/d/" + docId)
                .build();
    }

    @Transactional(readOnly = true)
    public DocumentResponses.DocumentResponse getDocument(String documentId, String userId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        CrdtDocument crdtDoc = crdtService.getOrCreateDocument(documentId);
        String content = crdtDoc.getText();

        List<DocumentCollaborator> collabs = collaboratorRepository.findByDocumentId(documentId);
        List<DocumentResponses.CollaboratorInfo> collabInfos = collabs.stream()
                .map(c -> {
                    var presence = presenceService.getPresence(documentId, c.getUserId());
                    return DocumentResponses.CollaboratorInfo.builder()
                            .userId(c.getUserId())
                            .name(c.getUserId()) // In production, look up from user service
                            .permission(c.getPermission().name())
                            .online(presence != null)
                            .cursorPosition(presence != null ? presence.cursorPosition() : null)
                            .build();
                })
                .toList();

        return DocumentResponses.DocumentResponse.builder()
                .documentId(doc.getDocumentId())
                .title(doc.getTitle())
                .ownerId(doc.getOwnerId())
                .content(content)
                .versionVector(crdtDoc.getVersionVector().toMap())
                .collaborators(collabInfos)
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .shareUrl("https://docs.example.com/d/" + documentId)
                .build();
    }

    @Transactional
    public DocumentResponses.DocumentResponse updateTitle(String documentId, String userId, DocumentRequests.UpdateTitleRequest request) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        doc.setTitle(request.getTitle());
        documentRepository.save(doc);

        log.info("Document title updated: {} by user {}", documentId, userId);

        return DocumentResponses.DocumentResponse.builder()
                .documentId(doc.getDocumentId())
                .title(doc.getTitle())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    @Transactional
    public void deleteDocument(String documentId, String userId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        if (!doc.getOwnerId().equals(userId)) {
            throw new IllegalStateException("Only the owner can delete a document");
        }

        crdtService.removeDocument(documentId);
        collaboratorRepository.deleteAll(collaboratorRepository.findByDocumentId(documentId));
        documentRepository.delete(doc);

        log.info("Document deleted: {} by user {}", documentId, userId);
    }

    @Transactional
    public DocumentResponses.ShareResponse shareDocument(String documentId, String userId, DocumentRequests.ShareDocumentRequest request) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        DocumentCollaborator.Permission perm = DocumentCollaborator.Permission.valueOf(request.getPermission().toUpperCase());
        DocumentCollaborator collab = DocumentCollaborator.builder()
                .documentId(documentId)
                .userId(request.getUserId())
                .permission(perm)
                .build();
        collaboratorRepository.save(collab);

        String shareId = "share_" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Document {} shared with user {} as {}", documentId, request.getUserId(), perm);

        return DocumentResponses.ShareResponse.builder()
                .shareId(shareId)
                .documentId(documentId)
                .userId(request.getUserId())
                .permission(perm.name())
                .sharedAt(Instant.now())
                .build();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponses.CollaboratorInfo> getCollaborators(String documentId) {
        List<DocumentCollaborator> collabs = collaboratorRepository.findByDocumentId(documentId);
        return collabs.stream()
                .map(c -> {
                    var presence = presenceService.getPresence(documentId, c.getUserId());
                    return DocumentResponses.CollaboratorInfo.builder()
                            .userId(c.getUserId())
                            .name(c.getUserId())
                            .permission(c.getPermission().name())
                            .online(presence != null)
                            .cursorPosition(presence != null ? presence.cursorPosition() : null)
                            .build();
                })
                .toList();
    }

    public boolean hasAccess(String documentId, String userId) {
        return collaboratorRepository.existsByDocumentIdAndUserId(documentId, userId);
    }
}

