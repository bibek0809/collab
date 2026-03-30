package com.example.bibek.demo.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks user presence (cursor position, selection, online status) for each document.
 * Uses in-memory storage with TTL-based eviction. In production, back this with Redis
 * for cross-instance sharing.
 */
@Service
@Slf4j
public class PresenceService {

    /** Map: documentId -> (userId -> PresenceInfo) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, PresenceInfo>> presenceMap
            = new ConcurrentHashMap<>();

    @Value("${collab.presence-ttl-seconds:1800}")
    private int presenceTtlSeconds;

    public record PresenceInfo(
            String userId,
            String userName,
            int cursorPosition,
            Integer selectionStart,
            Integer selectionEnd,
            Instant lastSeen
    ) {}

    /**
     * Update presence for a user in a document.
     */
    public void updatePresence(String documentId, String userId, String userName,
                               int cursorPosition, Integer selectionStart, Integer selectionEnd) {
        presenceMap.computeIfAbsent(documentId, k -> new ConcurrentHashMap<>())
                .put(userId, new PresenceInfo(
                        userId, userName, cursorPosition, selectionStart, selectionEnd, Instant.now()
                ));
    }

    /**
     * Get presence info for a specific user in a document.
     */
    public PresenceInfo getPresence(String documentId, String userId) {
        var docPresence = presenceMap.get(documentId);
        if (docPresence == null) return null;
        PresenceInfo info = docPresence.get(userId);
        if (info == null) return null;
        // Check TTL
        if (info.lastSeen().plusSeconds(presenceTtlSeconds).isBefore(Instant.now())) {
            docPresence.remove(userId);
            return null;
        }
        return info;
    }

    /**
     * Get all active users for a document.
     */
    public List<PresenceInfo> getActiveUsers(String documentId) {
        var docPresence = presenceMap.get(documentId);
        if (docPresence == null) return List.of();

        Instant cutoff = Instant.now().minusSeconds(presenceTtlSeconds);
        return docPresence.values().stream()
                .filter(p -> p.lastSeen().isAfter(cutoff))
                .toList();
    }

    /**
     * Remove a user's presence when they disconnect.
     */
    public void removePresence(String documentId, String userId) {
        var docPresence = presenceMap.get(documentId);
        if (docPresence != null) {
            docPresence.remove(userId);
            if (docPresence.isEmpty()) {
                presenceMap.remove(documentId);
            }
        }
    }

    /**
     * Periodically clean up stale presence entries.
     */
    @Scheduled(fixedRate = 60_000) // Every minute
    public void cleanupStalePresence() {
        Instant cutoff = Instant.now().minusSeconds(presenceTtlSeconds);
        presenceMap.forEach((docId, users) -> {
            users.entrySet().removeIf(e -> e.getValue().lastSeen().isBefore(cutoff));
            if (users.isEmpty()) presenceMap.remove(docId);
        });
    }

    public int getActiveDocumentCount() {
        return presenceMap.size();
    }

    public int getTotalActiveUsers() {
        return presenceMap.values().stream()
                .mapToInt(ConcurrentHashMap::size)
                .sum();
    }
}

