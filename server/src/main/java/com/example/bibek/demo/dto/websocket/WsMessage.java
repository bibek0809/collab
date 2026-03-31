package com.example.bibek.demo.dto.websocket;


import lombok.*;

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
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    final class JoinMessage implements WsMessage {
        private  String documentId;
        private  Map<String, Long> versionVector;
        @Override public String type() { return "JOIN"; }
    }

    @Getter @Builder
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    final class OperationMessage implements WsMessage {
        private  String documentId;
        private  String opType;      // INSERT, DELETE, FORMAT
        private  String characterId;
        private  String characterValue;
        private  String previousId;
        private  String siteId;
        private  long logicalTimestamp;
        private  Map<String, Long> versionVector;
        private  String userId;
        @Override public String type() { return "OPERATION"; }
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    final class CursorUpdateMessage implements WsMessage {
        private  String documentId;
        private  String userId;
        private  String userName;
        private  int cursorPosition;
        private  Integer selectionStart;
        private  Integer selectionEnd;
        @Override public String type() { return "CURSOR_UPDATE"; }
    }

    // ─── SERVER → CLIENT ───

    @Getter @Builder
    final class OperationAck implements WsMessage {
        private  String operationId;
        private  String status;
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
