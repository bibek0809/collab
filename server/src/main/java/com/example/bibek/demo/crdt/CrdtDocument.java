package com.example.bibek.demo.crdt;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * CRDT Document — a Replicated Growable Array (RGA) for collaborative text editing.
 *
 * Each character has a unique ID (siteId:logicalTimestamp) and a reference
 * to its predecessor. Deletions are handled via tombstones. Operations are
 * commutative: applying them in any order yields the same final document.
 *
 * Thread-safe via a read-write lock for concurrent access from WebSocket handlers.
 */
@Slf4j
public class CrdtDocument implements Serializable {

    /** Ordered list of all characters (including tombstoned deletions). */
    private final List<CrdtChar> chars;

    /** Fast lookup: characterId -> index in chars list. */
    private final Map<String, Integer> indexById;

    /** Version vector tracking causality. */
    @Getter
    private final VersionVector versionVector;

    /** Queue for operations waiting on unmet causal dependencies. */
    private final Deque<PendingOperation> pendingOps;

    /** Lock for thread safety. */
    private final ReentrantReadWriteLock lock;

    /** Counter for total operations applied (for snapshot scheduling). */
    @Getter
    private int operationCount;

    public CrdtDocument() {
        this.chars = new ArrayList<>();
        this.indexById = new HashMap<>();
        this.versionVector = new VersionVector();
        this.pendingOps = new ConcurrentLinkedDeque<>();
        this.lock = new ReentrantReadWriteLock();
        this.operationCount = 0;
    }

    // ─── LOCAL OPERATIONS (originating from this client/server) ───

    /**
     * Insert a character locally. Returns the CrdtChar created.
     *
     * @param value    The character to insert
     * @param afterId  The ID of the character to insert after (null = beginning)
     * @param siteId   The site performing the insert
     * @return The newly created CrdtChar
     */
    public CrdtChar localInsert(char value, String afterId, String siteId) {
        lock.writeLock().lock();
        try {
            long ts = versionVector.increment(siteId);
            String charId = CrdtChar.makeId(siteId, ts);

            CrdtChar newChar = CrdtChar.builder()
                    .id(charId)
                    .value(value)
                    .previousId(afterId)
                    .deleted(false)
                    .siteId(siteId)
                    .logicalTimestamp(ts)
                    .build();

            integrateInsert(newChar);
            operationCount++;
            return newChar;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Delete a character locally by marking it as a tombstone.
     *
     * @param charId The ID of the character to delete
     * @param siteId The site performing the delete
     * @return true if the character was found and deleted
     */
    public boolean localDelete(String charId, String siteId) {
        lock.writeLock().lock();
        try {
            versionVector.increment(siteId);
            boolean result = integrateDelete(charId);
            if (result) operationCount++;
            return result;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── REMOTE OPERATIONS (received from other sites) ───

    /**
     * Apply a remote insert operation. Checks causal dependencies first.
     */
    public boolean remoteInsert(CrdtChar newChar, VersionVector opVector) {
        lock.writeLock().lock();
        try {
            // Check if we can apply (causal dependency: previousId must exist or be null)
            if (newChar.getPreviousId() != null && !indexById.containsKey(newChar.getPreviousId())) {
                pendingOps.add(new PendingOperation(PendingOperation.Type.INSERT, newChar, null, opVector));
                log.debug("Queued insert for {} — waiting for dependency {}", newChar.getId(), newChar.getPreviousId());
                return false;
            }

            // Check if already applied (idempotency)
            if (indexById.containsKey(newChar.getId())) {
                log.debug("Skipping duplicate insert for {}", newChar.getId());
                return true;
            }

            integrateInsert(newChar);
            versionVector.update(newChar.getSiteId(), newChar.getLogicalTimestamp());
            operationCount++;

            // Try to apply any pending operations that now have their dependencies met
            processPendingOps();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Apply a remote delete operation.
     */
    public boolean remoteDelete(String charId, String siteId, long logicalTimestamp, VersionVector opVector) {
        lock.writeLock().lock();
        try {
            if (!indexById.containsKey(charId)) {
                pendingOps.add(new PendingOperation(PendingOperation.Type.DELETE, null, charId, opVector));
                log.debug("DELETE charId={}, exists={}", charId, indexById.containsKey(charId));
                log.debug("Queued delete for {} — character not yet present", charId);
                return false;
            }

            boolean result = integrateDelete(charId);
            versionVector.update(siteId, logicalTimestamp);
            if (result) operationCount++;

            processPendingOps();
            return result;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── CORE INTEGRATION LOGIC ───

    /**
     * Integrate a new character into the correct position in the sequence.
     * Uses the RGA algorithm: find the insertion point after previousId,
     * then position among concurrent siblings by (timestamp DESC, siteId ASC).
     */
    private void integrateInsert(CrdtChar newChar) {
        int insertPos;

        if (newChar.getPreviousId() == null) {
            // Insert at the beginning; scan for correct position among other "first" chars
            insertPos = 0;
            while (insertPos < chars.size()) {
                CrdtChar existing = chars.get(insertPos);
                if (existing.getPreviousId() != null) break;
                if (newChar.compareTo(existing) <= 0) break;
                insertPos++;
            }
        } else {
            Integer prevIdx = indexById.get(newChar.getPreviousId());
            if (prevIdx == null) {
                // Should not happen if causal dependencies are checked, but fallback to end
                insertPos = chars.size();
            } else {
                // Start after the predecessor
                insertPos = prevIdx + 1;

                // Walk past characters that also follow the same predecessor but have higher priority
                while (insertPos < chars.size()) {
                    CrdtChar existing = chars.get(insertPos);
                    // Stop if we've passed siblings of the same predecessor
                    if (existing.getPreviousId() != null
                            && !existing.getPreviousId().equals(newChar.getPreviousId())) {
                        break;
                    }
                    // Among siblings, order by compareTo (timestamp DESC, siteId ASC)
                    if (newChar.compareTo(existing) <= 0) break;
                    insertPos++;
                }
            }
        }

        chars.add(insertPos, newChar);
        rebuildIndex(); // Rebuild index after insertion (positions shifted)
    }

    private boolean integrateDelete(String charId) {
        Integer idx = indexById.get(charId);
        if (idx == null) return false;

        CrdtChar ch = chars.get(idx);
        if (ch.isDeleted()) return false; // Already deleted (idempotent)

        ch.setDeleted(true);
        return true;
    }

    /** Try to apply queued operations whose dependencies are now satisfied. */
    private void processPendingOps() {
        boolean progress = true;
        while (progress) {
            progress = false;
            Iterator<PendingOperation> it = pendingOps.iterator();
            while (it.hasNext()) {
                PendingOperation pending = it.next();
                boolean applied = switch (pending.type()) {
                    case INSERT -> {
                        CrdtChar ch = pending.character();
                        if (ch.getPreviousId() == null || indexById.containsKey(ch.getPreviousId())) {
                            if (!indexById.containsKey(ch.getId())) {
                                integrateInsert(ch);
                                versionVector.update(ch.getSiteId(), ch.getLogicalTimestamp());
                                operationCount++;
                            }
                            yield true;
                        }
                        yield false;
                    }
                    case DELETE -> {
                        if (indexById.containsKey(pending.charId())) {
                            integrateDelete(pending.charId());
                            operationCount++;
                            yield true;
                        }
                        yield false;
                    }
                };
                if (applied) {
                    it.remove();
                    progress = true;
                }
            }
        }
    }

    /** Rebuild the id-to-index lookup after structural changes. */
    private void rebuildIndex() {
        indexById.clear();
        for (int i = 0; i < chars.size(); i++) {
            indexById.put(chars.get(i).getId(), i);
        }
    }

    // ─── READ OPERATIONS ───

    /** Render the visible text (excluding tombstoned characters). */
    public String getText() {
        lock.readLock().lock();
        try {
            StringBuilder sb = new StringBuilder();
            for (CrdtChar ch : chars) {
                if (!ch.isDeleted()) {
                    sb.append(ch.getValue());
                }
            }
            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Get all characters (including tombstones) for serialization. */
    public List<CrdtChar> getAllChars() {
        lock.readLock().lock();
        try {
            return List.copyOf(chars);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Get the visible character at a given display position. */
    public CrdtChar getCharAtVisiblePosition(int visiblePos) {
        lock.readLock().lock();
        try {
            int count = 0;
            for (CrdtChar ch : chars) {
                if (!ch.isDeleted()) {
                    if (count == visiblePos) return ch;
                    count++;
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Get the ID of the character at a visible position (for insert-after). */
    public String getIdAtVisiblePosition(int visiblePos) {
        if (visiblePos < 0) return null; // Insert at beginning
        CrdtChar ch = getCharAtVisiblePosition(visiblePos);
        return ch != null ? ch.getId() : null;
    }

    /** Get visible document length. */
    public int getVisibleLength() {
        lock.readLock().lock();
        try {
            return (int) chars.stream().filter(c -> !c.isDeleted()).count();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── PENDING OPERATION RECORD ───

    private record PendingOperation(Type type, CrdtChar character, String charId, VersionVector vector) {
        enum Type { INSERT, DELETE }
    }
}

