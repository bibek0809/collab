import { useEffect, useRef, useCallback, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client/dist/sockjs';

/**
 * Custom hook for STOMP WebSocket communication.
 *
 * Subscribes to:
 *   /topic/document/{docId}            → operation broadcasts
 *   /topic/document/{docId}/presence   → cursor / online status
 *   /topic/document/{docId}/ack/{uid}  → operation acknowledgements
 *
 * Sends to:
 *   /app/document/{docId}/join         → join document
 *   /app/document/{docId}/operation    → send edit operation
 *   /app/document/{docId}/cursor       → send cursor position
 *   /app/document/{docId}/leave        → leave document
 */
export default function useWebSocket(documentId, userId, userName, callbacks = {}) {
  const { onOperation, onPresence, onAck } = callbacks;
  const clientRef = useRef(null);
  const [connected, setConnected] = useState(false);
  const reconnectAttempts = useRef(0);
  const maxReconnectAttempts = 5;

  // ── connect / disconnect lifecycle ─────────────────
  useEffect(() => {
    if (!documentId || !userId) return;

    // Use relative path so it goes through the proxy
    const wsUrl = `/ws/documents`;

    console.log('[WebSocket] Attempting connection to:', wsUrl, 'Attempt:', reconnectAttempts.current + 1);

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 5000,
      maxWebSocketChunkSize: 8 * 1024,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      connectionTimeout: 30000,
      debug: (msg) => console.log('[STOMP DEBUG]', msg),
    });

    client.onConnect = () => {
      console.log('[WebSocket] ✓ Connected successfully');
      reconnectAttempts.current = 0;
      setConnected(true);

      try {
        // ── subscribe: operation broadcasts ──
        client.subscribe(`/topic/document/${documentId}`, (msg) => {
          try {
            const data = JSON.parse(msg.body);
            console.log('[WebSocket] Received operation:', data);
            if (data.userId !== userId) onOperation?.(data);
          } catch (err) {
            console.error('[WebSocket] Failed to parse operation:', err);
          }
        });

        // ── subscribe: presence updates ──
        client.subscribe(`/topic/document/${documentId}/presence`, (msg) => {
          try {
            const data = JSON.parse(msg.body);
            console.log('[WebSocket] Presence update:', data);
            onPresence?.(data);
          } catch (err) {
            console.error('[WebSocket] Failed to parse presence:', err);
          }
        });

        // ── subscribe: ack for this user ──
        client.subscribe(`/topic/document/${documentId}/ack/${userId}`, (msg) => {
          try {
            const data = JSON.parse(msg.body);
            console.log('[WebSocket] Ack received:', data);
            onAck?.(data);
          } catch (err) {
            console.error('[WebSocket] Failed to parse ack:', err);
          }
        });

        // ── send: join document ──
        client.publish({
          destination: `/app/document/${documentId}/join`,
          body: JSON.stringify({ documentId, userId, userName, versionVector: {} }),
        });
        console.log('[WebSocket] Sent join message');
      } catch (err) {
        console.error('[WebSocket] Error during setup:', err);
        setConnected(false);
      }
    };

    client.onDisconnect = () => {
      console.log('[WebSocket] Disconnected');
      setConnected(false);
    };

    client.onStompError = (frame) => {
      console.error('[STOMP error]', frame.headers?.message, frame.body);
      setConnected(false);
    };

    client.onWebSocketClose = () => {
      console.log('[WebSocket] WebSocket closed');
      setConnected(false);
      if (reconnectAttempts.current < maxReconnectAttempts) {
        reconnectAttempts.current++;
        console.log(`[WebSocket] Will attempt reconnection (${reconnectAttempts.current}/${maxReconnectAttempts})`);
      }
    };

    client.onWebSocketError = (err) => {
      console.error('[WebSocket error]', err);
      setConnected(false);
    };

    client.activate();
    clientRef.current = client;

    return () => {
      console.log('[WebSocket] Cleaning up');
      // leave gracefully
      if (clientRef.current?.connected) {
        try {
          clientRef.current.publish({
            destination: `/app/document/${documentId}/leave`,
            body: JSON.stringify({ userId, documentId }),
          });
        } catch (err) {
          console.error('[WebSocket] Error sending leave:', err);
        }
      }
      client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentId, userId]);

  // ── send: edit operation ───────────────────────────
  const sendOperation = useCallback(
    (op) => {
      if (!clientRef.current?.connected) {
        console.warn('[WebSocket] ✗ Not connected, cannot send operation', op);
        return false;
      }
      try {
        console.log('[WebSocket] → Sending operation:', op);
        clientRef.current.publish({
          destination: `/app/document/${documentId}/operation`,
          body: JSON.stringify({ ...op, documentId, userId }),
        });
        console.log('[WebSocket] ✓ Operation published');
        return true;
      } catch (err) {
        console.error('[WebSocket] ✗ Failed to publish operation:', err);
        return false;
      }
    },
    [documentId, userId],
  );

  // ── send: cursor position ─────────────────────────
  const sendCursor = useCallback(
    (cursorPosition, selectionStart = null, selectionEnd = null) => {
      if (!clientRef.current?.connected) return;
      console.log('[WebSocket] Sending cursor:', { cursorPosition, selectionStart, selectionEnd });
      clientRef.current.publish({
        destination: `/app/document/${documentId}/cursor`,
        body: JSON.stringify({
          documentId,
          userId,
          userName,
          cursorPosition,
          selectionStart,
          selectionEnd,
        }),
      });
    },
    [documentId, userId, userName],
  );

  return { connected, sendOperation, sendCursor };
}
