package com.example.bibek.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${collab.websocket.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Clients subscribe to /topic/document/{docId} for broadcasts
        config.enableSimpleBroker("/topic", "/queue");
        // Clients send to /app/...
        config.setApplicationDestinationPrefixes("/app");
        // User-specific messages
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/documents")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();

        registry.addEndpoint("/ws/documents")
                .setAllowedOriginPatterns(allowedOrigins);
    }
}

