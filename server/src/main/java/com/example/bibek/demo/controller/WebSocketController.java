package com.example.bibek.demo.controller;

import com.example.bibek.demo.crdt.CrdtChar;
import com.example.bibek.demo.crdt.CrdtDocument;
import com.example.bibek.demo.crdt.VersionVector;
import com.example.bibek.demo.dto.websocket.WsMessage;
import com.example.bibek.demo.service.CrdtService;
import com.example.bibek.demo.service.KafkaEventService;
import com.example.bibek.demo.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

/**
 * WebSocket controller handling real-time collaborative editing messages.
 *
 * Message flow:
 * 1. Client subscribes to /topic/document/{docId}
 * 2. Client sends operations to /app/document/{docId}/operation
 * 3. Server applies CRDT operation, publishes to Kafka, broadcasts to subscribers
 * 4. Client sends cursor updates to /app/document/{docId}/cursor
 * 5. Server broadcasts presence updates to all subscribers
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final CrdtService crdtService;
    private final PresenceService presenceService;
    private final KafkaEventService kafkaEventService;
    private final SimpMessagingTemplate messagingTemplate;

    // ─── JOIN: Client subscribes and receives current state ───
    @MessageMapping("/document/{documentId}/join")
    public void handleJoin(@DestinationVariable String documentId,
                           @Payload WsMessage.JoinMessage message,
                           @Header("simpSessionId") String sessionId) {
        log.info("User joining document {} (session: {})", documentId, sessionId);

        CrdtDocument doc = crdtService.getOrCreateDocument(documentId);
        String content = doc.getText();
        Map<String, Long> versionVector = doc.getVersionVector().toMap();

        // Get active users
        List<WsMessage.PresenceUpdate> activeUsers = presenceService.getActiveUsers(documentId).stream()
                .map(p -> WsMessage.PresenceUpdate.builder()
                        .userId(p.userId())
                        .userName(p.userName())
                        .cursorPosition(p.cursorPosition())
                        .selectionStart(p.selectionStart())
                        .selectionEnd(p.selectionEnd())
                        .online(true)
                        .build())
                .toList();

        // Send current state to the joining client
        WsMessage.SyncStateMessage syncState = WsMessage.SyncStateMessage.builder()
                .documentId(documentId)
                .content(content)
                .versionVector(versionVector)
                .activeUsers(activeUsers)
                .build();

        messagingTemplate.convertAndSendToUser(
                sessionId, "/queue/sync", syncState);
    }

    // ─── OPERATION: Client sends an edit operation ───
    @MessageMapping("/document/{documentId}/operation")
    public void handleOperation(@DestinationVariable String documentId,
                                @Payload WsMessage.OperationMessage message) {
        log.debug("Operation received: {} on doc {} from user {}",
                message.getOpType(), documentId, message.getUserId());

        VersionVector opVector = new VersionVector(
                message.getVersionVector() != null ? message.getVersionVector() : Map.of());

        switch (message.getOpType()) {
            case "INSERT" -> {
                CrdtChar inserted = crdtService.applyInsert(
                        documentId,
                        message.getCharacterValue().charAt(0),
                        message.getPreviousId(),
                        message.getSiteId(),
                        message.getLogicalTimestamp(),
                        opVector
                );

                // Broadcast to all subscribers
                WsMessage.OperationBroadcast broadcast = WsMessage.OperationBroadcast.builder()
                        .opType("INSERT")
                        .characterId(inserted.getId())
                        .characterValue(String.valueOf(inserted.getValue()))
                        .previousId(inserted.getPreviousId())
                        .siteId(inserted.getSiteId())
                        .logicalTimestamp(inserted.getLogicalTimestamp())
                        .versionVector(crdtService.getVersionVector(documentId))
                        .userId(message.getUserId())
                        .build();

                messagingTemplate.convertAndSend(
                        "/topic/document/" + documentId, broadcast);

                // Publish to Kafka for cross-instance sync
                kafkaEventService.publishOperationAsync(
                        documentId, "INSERT",
                        inserted.getId(),
                        String.valueOf(inserted.getValue()),
                        inserted.getPreviousId(),
                        inserted.getSiteId(),
                        inserted.getLogicalTimestamp(),
                        crdtService.getVersionVector(documentId),
                        message.getUserId()
                );


                // Send ACK to sender
                WsMessage.OperationAck ack = WsMessage.OperationAck.builder()
                        .operationId(inserted.getId())
                        .status("APPLIED")
                        .build();
                messagingTemplate.convertAndSend(
                        "/topic/document/" + documentId + "/ack/" + message.getUserId(), ack);
            }

            case "DELETE" -> {
                boolean deleted = crdtService.applyDelete(
                        documentId,
                        message.getCharacterId(),
                        message.getSiteId(),
                        message.getLogicalTimestamp(),
                        opVector
                );

                if (deleted) {
                    WsMessage.OperationBroadcast broadcast = WsMessage.OperationBroadcast.builder()
                            .opType("DELETE")
                            .characterId(message.getCharacterId())
                            .siteId(message.getSiteId())
                            .logicalTimestamp(message.getLogicalTimestamp())
                            .versionVector(crdtService.getVersionVector(documentId))
                            .userId(message.getUserId())
                            .build();

                    messagingTemplate.convertAndSend(
                            "/topic/document/" + documentId, broadcast);

                    kafkaEventService.publishOperationAsync(
                            documentId, "DELETE",
                            message.getCharacterId(),
                            null,
                            null,
                            message.getSiteId(),
                            message.getLogicalTimestamp(),
                            crdtService.getVersionVector(documentId),
                            message.getUserId()
                    );
                }

                WsMessage.OperationAck ack = WsMessage.OperationAck.builder()
                        .operationId(message.getCharacterId())
//                        .status(deleted ? "APPLIED" : "NOOP")
                        .status(deleted ? "APPLIED" : "PENDING")
                        .build();
                messagingTemplate.convertAndSend(
                        "/topic/document/" + documentId + "/ack/" + message.getUserId(), ack);
            }

            default -> log.warn("Unknown operation type: {}", message.getOpType());
        }
    }

    // ─── CURSOR: Client sends cursor/selection update ───

    @MessageMapping("/document/{documentId}/cursor")
    public void handleCursorUpdate(@DestinationVariable String documentId,
                                   @Payload WsMessage.CursorUpdateMessage message) {
        presenceService.updatePresence(
                documentId,
                message.getUserId(),
                message.getUserName(),
                message.getCursorPosition(),
                message.getSelectionStart(),
                message.getSelectionEnd()
        );

        // Broadcast presence to all subscribers
        WsMessage.PresenceUpdate update = WsMessage.PresenceUpdate.builder()
                .userId(message.getUserId())
                .userName(message.getUserName())
                .cursorPosition(message.getCursorPosition())
                .selectionStart(message.getSelectionStart())
                .selectionEnd(message.getSelectionEnd())
                .online(true)
                .build();

        messagingTemplate.convertAndSend(
                "/topic/document/" + documentId + "/presence", update);
    }

    // ─── LEAVE: Client disconnects ───

    @MessageMapping("/document/{documentId}/leave")
    public void handleLeave(@DestinationVariable String documentId,
                            @Payload Map<String, String> payload) {
        String userId = payload.get("userId");
        presenceService.removePresence(documentId, userId);

        WsMessage.PresenceUpdate update = WsMessage.PresenceUpdate.builder()
                .userId(userId)
                .online(false)
                .build();

        messagingTemplate.convertAndSend(
                "/topic/document/" + documentId + "/presence", update);

        log.info("User {} left document {}", userId, documentId);
    }
}

