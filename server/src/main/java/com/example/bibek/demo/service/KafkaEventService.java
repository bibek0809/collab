package com.example.bibek.demo.service;

import com.example.bibek.demo.config.KafkaConfig;
import com.example.bibek.demo.crdt.VersionVector;
import com.example.bibek.demo.dto.websocket.WsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles Kafka-based event publishing and consumption for cross-instance
 * and cross-region synchronization of document operations.
 *
 * When Kafka is not available, operations are still broadcast via WebSocket
 * within the same instance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaEventService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CrdtService crdtService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${collab.region:us-east}")
    private String currentRegion;

    @Value("${collab.kafka.publish-enabled:false}")
    private boolean publishEnabled;

    /**
     * Publish an operation event to Kafka for cross-instance distribution.
     */
    public void publishOperationAsync(String documentId, String opType,
                                 String characterId, String characterValue,
                                 String previousId, String siteId,
                                 long logicalTimestamp, Map<String, Long> versionVector,
                                 String userId) {
        if (!publishEnabled || kafkaTemplate == null) {        // ← CHECK FLAG FIRST
            return;
        }

        try {

            WsMessage.DocumentOperationEvent event = WsMessage.DocumentOperationEvent.builder()
                    .eventId("evt_" + UUID.randomUUID().toString().substring(0, 12))
                    .eventType("DocumentOperationEvent")
                    .documentId(documentId)
                    .siteId(siteId)
                    .opType(opType)
                    .characterId(characterId)
                    .characterValue(characterValue)
                    .previousId(previousId)
                    .logicalTimestamp(logicalTimestamp)
                    .versionVector(versionVector)
                    .userId(userId)
                    .sourceRegion(currentRegion)
                    .timestamp(Instant.now())
                    .build();

//            kafkaTemplate.send(KafkaConfig.DOCUMENT_OPERATIONS_TOPIC, documentId, event);
            kafkaTemplate.send(KafkaConfig.DOCUMENT_OPERATIONS_TOPIC, documentId, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Kafka publish failed ...");  // ← warn, not error
                        } else {
                            log.debug("Kafka publish OK ...");
                        }
                    });
            log.debug("Published operation event for doc {} char {}", documentId, characterId);
        } catch (Exception e) {
            log.warn("Failed to publish to Kafka (may not be available): {}", e.getMessage());
            // Operations still work via direct WebSocket broadcast
        }
    }

    /**
     * Consume operation events from Kafka (from other instances or regions).
     */
    @KafkaListener(
            topics = KafkaConfig.DOCUMENT_OPERATIONS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}",
            autoStartup = "${collab.kafka.publish-enabled:false}"
    )
    public void consumeOperation(WsMessage.DocumentOperationEvent event) {
        log.debug("Received Kafka event: {} for doc {}", event.getEventId(), event.getDocumentId());

        // Skip events from our own region (already applied)
        if (currentRegion.equals(event.getSourceRegion())) {
            return;
        }

        String documentId = event.getDocumentId();
        VersionVector opVector = new VersionVector(event.getVersionVector());

        switch (event.getOpType()) {
            case "INSERT" -> {
                crdtService.applyInsert(
                        documentId,
                        event.getCharacterValue().charAt(0),
                        event.getPreviousId(),
                        event.getSiteId(),
                        event.getLogicalTimestamp(),
                        opVector
                );
            }
            case "DELETE" -> {
                crdtService.applyDelete(
                        documentId,
                        event.getCharacterId(),
                        event.getSiteId(),
                        event.getLogicalTimestamp(),
                        opVector
                );
            }
        }

        // Broadcast to local WebSocket clients
        WsMessage.OperationBroadcast broadcast = WsMessage.OperationBroadcast.builder()
                .opType(event.getOpType())
                .characterId(event.getCharacterId())
                .characterValue(event.getCharacterValue())
                .previousId(event.getPreviousId())
                .siteId(event.getSiteId())
                .logicalTimestamp(event.getLogicalTimestamp())
                .versionVector(event.getVersionVector())
                .userId(event.getUserId())
                .build();

        messagingTemplate.convertAndSend("/topic/document/" + documentId, broadcast);
    }
}
