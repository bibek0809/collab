package com.example.bibek.demo.crdt;

import lombok.Getter;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version Vector (Vector Clock) for tracking causality.
 * Maps each site ID to its latest known logical timestamp.
 * Used to determine causal ordering and detect concurrent operations.
 */
@Getter
public class VersionVector implements Serializable {

    private final ConcurrentHashMap<String, Long> clock;

    public VersionVector() {
        this.clock = new ConcurrentHashMap<>();
    }

    public VersionVector(Map<String, Long> initial) {
        this.clock = new ConcurrentHashMap<>(initial);
    }

    /** Increment the clock for a given site and return the new value. */
    public long increment(String siteId) {
        return clock.merge(siteId, 1L, Long::sum);
    }

    /** Get the current timestamp for a site (0 if unseen). */
    public long get(String siteId) {
        return clock.getOrDefault(siteId, 0L);
    }

    /** Update this vector to include information from another vector (point-wise max). */
    public void merge(VersionVector other) {
        other.clock.forEach((site, ts) ->
                clock.merge(site, ts, Long::max));
    }

    /** Update a single site's timestamp if the given value is greater. */
    public void update(String siteId, long timestamp) {
        clock.merge(siteId, timestamp, Long::max);
    }

    /**
     * Check if this vector dominates (or equals) another.
     * True means we have seen all operations the other has seen.
     */
    public boolean dominates(VersionVector other) {
        for (Map.Entry<String, Long> entry : other.clock.entrySet()) {
            if (get(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if two vectors are concurrent (neither dominates the other).
     */
    public boolean isConcurrentWith(VersionVector other) {
        return !this.dominates(other) && !other.dominates(this);
    }

    /** Return a snapshot as an immutable map. */
    public Map<String, Long> toMap() {
        return Map.copyOf(clock);
    }

    @Override
    public String toString() {
        return clock.toString();
    }
}

