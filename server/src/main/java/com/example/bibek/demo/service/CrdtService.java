package com.example.bibek.demo.service;

import com.example.bibek.demo.crdt.CrdtChar;
import com.example.bibek.demo.crdt.CrdtDocument;
import com.example.bibek.demo.crdt.VersionVector;
import com.example.bibek.demo.model.DocumentOperation;
import com.example.bibek.demo.model.DocumentSnapshot;
import com.example.bibek.demo.repo.DocumentOperationRepository;
import com.example.bibek.demo.repo.DocumentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the in-memory CRDT document state and synchronization with persistent storage.
 * Each active document is held in a ConcurrentHashMap for fast access.
 * Operations are persisted to the database for durability and replayed on recovery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrdtService {

    private final DocumentOperationRepository operationRepository;
    private final DocumentSnapshotRepository snapshotRepository;

    /** In-memory cache of active CRDT documents. */
    private final ConcurrentHashMap<String, CrdtDocument> activeDocuments = new ConcurrentHashMap<>();

    /** Track operation counts since last snapshot per document. */
    private final ConcurrentHashMap<String, Integer> opsSinceSnapshot = new ConcurrentHashMap<>();

    @Value("${collab.snapshot-interval:1000}")
    private int snapshotInterval;

    // ─── DOCUMENT LIFECYCLE ───
    /**
     * Get or create a CRDT document. If not in memory, rebuild from DB operations.
     */
    public CrdtDocument getOrCreateDocument(String documentId) {
        return activeDocuments.computeIfAbsent(documentId, this::rebuildFromStorage);
    }

    /**
     * Remove a document from the in-memory cache.
     */
    public void removeDocument(String documentId) {
        activeDocuments.remove(documentId);
        opsSinceSnapshot.remove(documentId);
    }

    /**
     * Rebuild a CRDT document from the latest snapshot + subsequent operations.
     */
    private CrdtDocument rebuildFromStorage(String documentId) {
        log.info("Rebuilding CRDT document {} from storage", documentId);
        CrdtDocument doc = new CrdtDocument();

        // 1. Try to load from latest snapshot
        List<DocumentSnapshot> snapshots = snapshotRepository
                .findLatestSnapshots(documentId, PageRequest.of(0, 1));

        long afterOpId = 0;
        if (!snapshots.isEmpty()) {
//            DocumentSnapshot snapshot = snapshots.getFirst();
            DocumentSnapshot snapshot = snapshots.get(0);
            // Replay snapshot content into the CRDT
            String siteId = "snapshot";
            String prevId = null;
            for (char c : snapshot.getContent().toCharArray()) {
                CrdtChar inserted = doc.localInsert(c, prevId, siteId);
                prevId = inserted.getId();
            }
            // Restore version vector
            snapshot.getVersionVector().forEach((site, ts) ->
                    doc.getVersionVector().update(site, ts));
            afterOpId = snapshot.getOperationCount();
            log.info("Loaded snapshot for {} with {} operations", documentId, afterOpId);
        }

        // 2. Replay operations after the snapshot
        List<DocumentOperation> ops = afterOpId > 0
                ? operationRepository.findOperationsAfter(documentId, afterOpId)
                : operationRepository.findByDocumentIdOrderByLogicalTimestampAsc(documentId);

        for (DocumentOperation op : ops) {
            applyStoredOperation(doc, op);
        }

        log.info("Rebuilt document {} with {} total chars, text length {}",
                documentId, doc.getAllChars().size(), doc.getVisibleLength());
        return doc;
    }

    private void applyStoredOperation(CrdtDocument doc, DocumentOperation op) {
        switch (op.getOperationType()) {
            case INSERT -> {
                CrdtChar ch = CrdtChar.builder()
                        .id(op.getCharacterId())
                        .value(op.getCharacterValue() != null ? op.getCharacterValue().charAt(0) : ' ')
                        .previousId(op.getPreviousId())
                        .deleted(op.getDeleted())
                        .siteId(op.getSiteId())
                        .logicalTimestamp(op.getLogicalTimestamp())
                        .build();
                doc.remoteInsert(ch, new VersionVector());
            }
            case DELETE -> doc.remoteDelete(
                    op.getCharacterId(),
                    op.getSiteId(),
                    op.getLogicalTimestamp(),
                    new VersionVector()
            );
            case FORMAT -> {
                // Format operations can be extended here
            }
        }
    }

    // ─── OPERATION HANDLING ───

    /**
     * Apply an insert operation (from a remote client) and persist it.
     */
    @Transactional
    public CrdtChar applyInsert(String documentId, char value, String afterId,
                                String siteId, long logicalTimestamp,
                                VersionVector opVector) {
        CrdtDocument doc = getOrCreateDocument(documentId);
        String charId = CrdtChar.makeId(siteId, logicalTimestamp);

        CrdtChar newChar = CrdtChar.builder()
                .id(charId)
                .value(value)
                .previousId(afterId)
                .deleted(false)
                .siteId(siteId)
                .logicalTimestamp(logicalTimestamp)
                .build();

        doc.remoteInsert(newChar, opVector);

        // Persist to database
        persistSingleOperation(documentId, newChar, DocumentOperation.OperationType.INSERT);

        // Check if we should create a snapshot
        checkAndCreateSnapshot(documentId, doc);

        return newChar;
    }

    /**
     * Apply a delete operation and persist it.
     */
    @Transactional
    public boolean applyDelete(String documentId, String charId,
                               String siteId, long logicalTimestamp,
                               VersionVector opVector) {
        CrdtDocument doc = getOrCreateDocument(documentId);
        boolean deleted = doc.remoteDelete(charId, siteId, logicalTimestamp, opVector);

        if (deleted) {
            // Update the existing operation record to mark as deleted
            // Or persist a DELETE operation
            DocumentOperation deleteOp = DocumentOperation.builder()
                    .documentId(documentId)
                    .siteId(siteId)
                    .logicalTimestamp(logicalTimestamp)
                    .operationType(DocumentOperation.OperationType.DELETE)
                    .characterId(charId)
                    .deleted(true)
                    .createdAt(Instant.now())
                    .build();

            try {
                operationRepository.save(deleteOp);
            } catch (Exception e) {
                log.warn("Delete operation already persisted for char {}", charId);
            }

            checkAndCreateSnapshot(documentId, doc);
        }
        return deleted;
    }

    /**
     * Perform a local insert (server-side, e.g., for initial content).
     */
    public CrdtChar localInsert(String documentId, char value, String afterId, String siteId) {
        CrdtDocument doc = getOrCreateDocument(documentId);
        CrdtChar inserted = doc.localInsert(value, afterId, siteId);
        persistSingleOperation(documentId, inserted, DocumentOperation.OperationType.INSERT);
        checkAndCreateSnapshot(documentId, doc);
        return inserted;
    }

    // ─── PERSISTENCE ───

    private void persistSingleOperation(String documentId, CrdtChar ch,
                                        DocumentOperation.OperationType opType) {
        if (operationRepository.existsByDocumentIdAndCharacterId(documentId, ch.getId())) {
            return; // Idempotent
        }

        DocumentOperation op = DocumentOperation.builder()
                .documentId(documentId)
                .siteId(ch.getSiteId())
                .logicalTimestamp(ch.getLogicalTimestamp())
                .operationType(opType)
                .characterId(ch.getId())
                .characterValue(String.valueOf(ch.getValue()))
                .previousId(ch.getPreviousId())
                .deleted(ch.isDeleted())
                .createdAt(Instant.now())
                .build();

        operationRepository.save(op);
    }

    /**
     * Persist all operations for a document (used during initial content creation).
     */
    @Transactional
    public void persistOperations(String documentId, CrdtDocument doc) {
        List<CrdtChar> chars = doc.getAllChars();
        for (CrdtChar ch : chars) {
            persistSingleOperation(documentId, ch, DocumentOperation.OperationType.INSERT);
        }
    }

    // ─── SNAPSHOTS ───

    private void checkAndCreateSnapshot(String documentId, CrdtDocument doc) {
        int count = opsSinceSnapshot.merge(documentId, 1, Integer::sum);
        if (count >= snapshotInterval) {
            createSnapshot(documentId, doc);
            opsSinceSnapshot.put(documentId, 0);
        }
    }

    @Transactional
    public void createSnapshot(String documentId, CrdtDocument doc) {
        String snapshotId = "snap_" + UUID.randomUUID().toString().substring(0, 12);
        DocumentSnapshot snapshot = DocumentSnapshot.builder()
                .snapshotId(snapshotId)
                .documentId(documentId)
                .content(doc.getText())
                .versionVector(doc.getVersionVector().toMap())
                .operationCount(doc.getOperationCount())
                .createdAt(Instant.now())
                .build();
        snapshotRepository.save(snapshot);
        log.info("Created snapshot {} for document {} at operation count {}",
                snapshotId, documentId, doc.getOperationCount());
    }

    // ─── SCHEDULED TASKS ───

    /**
     * Periodically evict inactive documents from memory.
     */
    @Scheduled(fixedRate = 300_000) // Every 5 minutes
    public void evictInactiveDocuments() {
        // In production, track last access time and evict idle documents
        log.debug("Active documents in memory: {}", activeDocuments.size());
    }

    // ─── QUERIES ───

    public String getDocumentText(String documentId) {
        return getOrCreateDocument(documentId).getText();
    }

    public Map<String, Long> getVersionVector(String documentId) {
        return getOrCreateDocument(documentId).getVersionVector().toMap();
    }

    public int getActiveDocumentCount() {
        return activeDocuments.size();
    }
}

