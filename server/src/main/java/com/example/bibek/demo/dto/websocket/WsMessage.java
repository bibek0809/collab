package com.example.bibek.demo.dto.websocket;


import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * WebSocket message types for real-time collaborative editing.
 */
public sealed interface WsMessage extends Serializable {

    String type();

    // ─── CLIENT → SERVER ───

    @Getter
    @Builder
    final class JoinMessage implements WsMessage {
        private final String documentId;
        private final Map<String, Long> versionVector;
        @Override public String type() { return "JOIN"; }
    }

    @Getter @Builder
    final class OperationMessage implements WsMessage {
        private final String documentId;
        private final String opType;      // INSERT, DELETE, FORMAT
        private final String characterId;
        private final String characterValue;
        private final String previousId;
        private final String siteId;
        private final long logicalTimestamp;
        private final Map<String, Long> versionVector;
        private final String userId;
        @Override public String type() { return "OPERATION"; }
    }

    @Getter @Builder
    final class CursorUpdateMessage implements WsMessage {
        private final String documentId;
        private final String userId;
        private final String userName;
        private final int cursorPosition;
        private final Integer selectionStart;
        private final Integer selectionEnd;
        @Override public String type() { return "CURSOR_UPDATE"; }
    }

    // ─── SERVER → CLIENT ───

    @Getter @Builder
    final class OperationAck implements WsMessage {
        private final String operationId;
        private final String status;
        @Override public String type() { return "OPERATION_ACK"; }
    }

    @Getter @Builder
    final class OperationBroadcast implements WsMessage {
        private final String opType;
        private final String characterId;
        private final String characterValue;
        private final String previousId;
        private final String siteId;
        private final long logicalTimestamp;
        private final Map<String, Long> versionVector;
        private final String userId;
        @Override public String type() { return "OPERATION_BROADCAST"; }
    }

    @Getter @Builder
    final class PresenceUpdate implements WsMessage {
        private final String userId;
        private final String userName;
        private final int cursorPosition;
        private final Integer selectionStart;
        private final Integer selectionEnd;
        private final boolean online;
        @Override public String type() { return "PRESENCE_UPDATE"; }
    }

    @Getter @Builder
    final class ErrorMessage implements WsMessage {
        private final String code;
        private final String message;
        @Override public String type() { return "ERROR"; }
    }

    @Getter @Builder
    final class SyncStateMessage implements WsMessage {
        private final String documentId;
        private final String content;
        private final Map<String, Long> versionVector;
        private final java.util.List<PresenceUpdate> activeUsers;
        @Override public String type() { return "SYNC_STATE"; }
    }

    // ─── KAFKA EVENT WRAPPER ───

    @Getter @Builder
    final class DocumentOperationEvent implements WsMessage, Serializable {
        private final String eventId;
        private final String eventType;
        private final String documentId;
        private final String siteId;
        private final String opType;
        private final String characterId;
        private final String characterValue;
        private final String previousId;
        private final long logicalTimestamp;
        private final Map<String, Long> versionVector;
        private final String userId;
        private final String sourceRegion;
        private final Instant timestamp;
        @Override public String type() { return "KAFKA_EVENT"; }
    }
}
