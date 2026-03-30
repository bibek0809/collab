package com.example.bibek.demo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaConfig {

    public static final String DOCUMENT_OPERATIONS_TOPIC = "document-operations";
    public static final String DOCUMENT_PRESENCE_TOPIC = "document-presence";

    @Bean
    public NewTopic documentOperationsTopic() {
        return TopicBuilder.name(DOCUMENT_OPERATIONS_TOPIC)
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic documentPresenceTopic() {
        return TopicBuilder.name(DOCUMENT_PRESENCE_TOPIC)
                .partitions(6)
                .replicas(1)
                .build();
    }
}
