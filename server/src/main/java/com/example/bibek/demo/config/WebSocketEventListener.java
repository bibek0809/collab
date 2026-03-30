package com.example.bibek.demo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final AtomicInteger activeConnections = new AtomicInteger(0);

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        int count = activeConnections.incrementAndGet();
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("WebSocket connected: session={}, total={}", accessor.getSessionId(), count);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        int count = activeConnections.decrementAndGet();
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("WebSocket disconnected: session={}, total={}", accessor.getSessionId(), count);
    }

    public int getActiveConnectionCount() {
        return activeConnections.get();
    }
}

