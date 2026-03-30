package com.example.bibek.demo.service;

import com.example.bibek.demo.dto.response.DocumentResponses;
import com.example.bibek.demo.model.DocumentSnapshot;
import com.example.bibek.demo.repo.DocumentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VersionHistoryService {

    private final DocumentSnapshotRepository snapshotRepository;
    private final CrdtService crdtService;

    @Transactional(readOnly = true)
    public DocumentResponses.VersionHistoryResponse getHistory(String documentId, int limit) {
        List<DocumentSnapshot> snapshots = snapshotRepository
                .findLatestSnapshots(documentId, PageRequest.of(0, limit));

        List<DocumentResponses.VersionInfo> versions = snapshots.stream()
                .map(s -> DocumentResponses.VersionInfo.builder()
                        .versionId(s.getSnapshotId())
                        .content(s.getContent())
                        .versionVector(s.getVersionVector())
                        .createdBy("system") // Could track this in snapshot
                        .createdAt(s.getCreatedAt())
                        .build())
                .toList();

        return DocumentResponses.VersionHistoryResponse.builder()
                .versions(versions)
                .totalVersions(versions.size())
                .build();
    }

    @Transactional
    public void restoreVersion(String documentId, String versionId) {
        DocumentSnapshot snapshot = snapshotRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));

        if (!snapshot.getDocumentId().equals(documentId)) {
            throw new IllegalArgumentException("Version does not belong to document");
        }

        // Remove current in-memory state and rebuild from snapshot
        crdtService.removeDocument(documentId);
        // The next getOrCreateDocument call will rebuild from the latest snapshot
        log.info("Restored document {} to version {}", documentId, versionId);
    }
}