# Collaborative Document Editor

A real-time collaborative document editor built with **Java 21**, **Spring Boot 3.4**, and **CRDTs** (Conflict-free Replicated Data Types).

## Architecture Overview

```
┌─────────────┐   WebSocket    ┌──────────────────┐    Kafka     ┌──────────────────┐
│   Client A   │◄──────────────►│                  │◄────────────►│   Other Region   │
│   Client B   │◄──────────────►│  Spring Boot App │              │   (Replica)      │
│   Client C   │◄──────────────►│                  │              └──────────────────┘
└─────────────┘                 └────────┬─────────┘
                                         │
                                ┌────────┴─────────┐
                                │                  │
                           ┌────▼────┐      ┌──────▼──────┐
                           │   H2 /  │      │   Redis     │
                           │  Postgres│      │  (Presence) │
                           └─────────┘      └─────────────┘
```

## Core Concepts

### CRDT (RGA - Replicated Growable Array)
Each character has a globally unique ID (`siteId:logicalTimestamp`) and a reference
to its predecessor. Operations (insert/delete) are **commutative** — applying them
in any order produces the same final document state.

### Version Vectors
Track causality across sites. Each site maintains a vector of known timestamps
from all other sites, ensuring operations are applied in causal order.

### Tombstones
Deleted characters are marked with a tombstone flag rather than removed. This
ensures that concurrent inserts referencing a deleted character can still be
integrated correctly.

## Tech Stack

| Layer          | Technology                        |
|----------------|-----------------------------------|
| Language       | Java 21 (records, sealed classes) |
| Framework      | Spring Boot 3.4                   |
| WebSocket      | Spring WebSocket + STOMP          |
| Database       | H2 (dev) / PostgreSQL (prod)      |
| Cache          | Redis (presence, sessions)        |
| Event Bus      | Apache Kafka (cross-instance)     |
| Metrics        | Micrometer + Prometheus           |

## Project Structure

```
src/main/java/com/collabeditor/
├── CollaborativeEditorApplication.java   # Main entry point
├── config/
│   ├── WebSocketConfig.java              # STOMP WebSocket setup
│   ├── WebSocketEventListener.java       # Connection tracking
│   ├── RedisConfig.java                  # Redis template
│   ├── KafkaConfig.java                  # Kafka topics
│   └── JacksonConfig.java               # JSON serialization
├── controller/
│   ├── DocumentController.java           # REST API endpoints
│   ├── WebSocketController.java          # Real-time editing
│   └── HealthController.java             # Health check
├── crdt/
│   ├── CrdtChar.java                     # Character with unique ID
│   ├── CrdtDocument.java                 # RGA implementation
│   └── VersionVector.java                # Causality tracking
├── dto/
│   ├── request/DocumentRequests.java     # API request DTOs
│   ├── response/DocumentResponses.java   # API response DTOs
│   └── websocket/WsMessage.java          # WebSocket messages
├── exception/
│   └── DocumentNotFoundException.java    # Exception + handler
├── model/
│   ├── Document.java                     # Document entity
│   ├── DocumentOperation.java            # CRDT operation entity
│   ├── DocumentSnapshot.java             # Snapshot entity
│   ├── DocumentCollaborator.java         # Permissions entity
│   └── DocumentCollaboratorId.java       # Composite key
├── repository/
│   └── DocumentRepository.java           # JPA repositories
└── service/
    ├── DocumentService.java              # Document CRUD
    ├── CrdtService.java                  # CRDT state management
    ├── PresenceService.java              # User presence tracking
    ├── VersionHistoryService.java        # Snapshots & restore
    └── KafkaEventService.java            # Cross-instance sync
```

## API Reference

### REST Endpoints

| Method | Endpoint                                | Description             |
|--------|-----------------------------------------|-------------------------|
| POST   | `/api/v1/documents`                     | Create document         |
| GET    | `/api/v1/documents/{id}`                | Get document + content  |
| PUT    | `/api/v1/documents/{id}/title`          | Update title            |
| DELETE | `/api/v1/documents/{id}`                | Delete document         |
| POST   | `/api/v1/documents/{id}/share`          | Share with user         |
| GET    | `/api/v1/documents/{id}/collaborators`  | List collaborators      |
| GET    | `/api/v1/documents/{id}/history`        | Version history         |
| POST   | `/api/v1/documents/{id}/restore`        | Restore version         |
| GET    | `/health`                               | Health check            |

### WebSocket (STOMP)

**Connect:** `ws://localhost:8080/ws/documents`

**Client → Server:**
- `/app/document/{docId}/join` — Join & sync state
- `/app/document/{docId}/operation` — Send edit operation
- `/app/document/{docId}/cursor` — Update cursor position
- `/app/document/{docId}/leave` — Leave document

**Server → Client (Subscribe):**
- `/topic/document/{docId}` — Operation broadcasts
- `/topic/document/{docId}/presence` — Presence updates
- `/topic/document/{docId}/ack/{userId}` — Operation ACKs
- `/user/{sessionId}/queue/sync` — Initial sync state

## Running

### Development (H2, no Redis/Kafka)

```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080` with H2 in-memory database.
H2 Console: `http://localhost:8080/h2-console`

### Production (PostgreSQL + Redis + Kafka)

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/collabeditor
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=secret
export REDIS_HOST=redis-host
export KAFKA_BOOTSTRAP_SERVERS=kafka:9092
export COLLAB_REGION=us-east

java -jar target/collaborative-editor-1.0.0.jar
```

## Design Decisions

1. **CRDT over OT** — Simpler to implement correctly, truly distributed, proven convergence
2. **Optimistic updates** — Users see changes immediately, CRDT handles conflicts
3. **Eventual consistency** — AP from CAP theorem; brief inconsistency is acceptable
4. **Snapshots every 1000 ops** — Fast recovery without replaying full history
5. **Tombstones for deletes** — Ensures concurrent operations resolve correctly
6. **Version vectors** — Track causality without a centralized clock
