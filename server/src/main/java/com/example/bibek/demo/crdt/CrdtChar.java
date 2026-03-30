package com.example.bibek.demo.crdt;


import lombok.*;

import java.io.Serializable;

/**
 * Represents a single character in the CRDT document.
 * Each character has a globally unique ID (siteId:logicalTimestamp),
 * a reference to the character it was inserted after (previousId),
 * and a tombstone flag for deletions.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrdtChar implements Serializable, Comparable<CrdtChar> {

    /** Globally unique ID: "siteId:logicalTimestamp" */
    private String id;

    /** The actual character value */
    private char value;

    /** ID of the character this was inserted after (null for first char) */
    private String previousId;

    /** Tombstone marker — true means this character is deleted */
    @Builder.Default
    private boolean deleted = false;

    /** Site (client) that created this character */
    private String siteId;

    /** Logical timestamp at creation time */
    private long logicalTimestamp;

    /**
     * Ordering: first by previousId chain, then by (logicalTimestamp DESC, siteId ASC)
     * for characters sharing the same previousId (concurrent inserts).
     */
    @Override
    public int compareTo(CrdtChar other) {
        // Compare by logical timestamp descending (newer chars first among siblings)
        int cmp = Long.compare(other.logicalTimestamp, this.logicalTimestamp);
        if (cmp != 0) return cmp;
        // Tie-break by siteId ascending for deterministic ordering
        return this.siteId.compareTo(other.siteId);
    }

    public static String makeId(String siteId, long timestamp) {
        return siteId + ":" + timestamp;
    }
}

