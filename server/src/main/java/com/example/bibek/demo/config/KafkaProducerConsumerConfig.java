package com.example.bibek.demo.config;

import com.example.bibek.demo.dto.websocket.WsMessage.DocumentOperationEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer and Consumer configuration using Spring Kafka 4.0+
 * non-deprecated classes: {@link JacksonJsonSerializer} and {@link JacksonJsonDeserializer}.
 *
 * <p>These replace the deprecated {@code JsonSerializer}/{@code JsonDeserializer}
 * and are the forward-compatible path for Jackson 3.
 *
 * <p>Producer: Sends DocumentOperationEvent as JSON with the documentId as the
 * partition key so operations for the same document land on the same partition.
 *
 * <p>Consumer: Deserializes JSON into DocumentOperationEvent with an error-handling
 * wrapper so poison-pill messages don't kill the listener. Failed messages are
 * retried 3 times with a 1-second backoff, then logged and skipped.
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaProducerConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:collab-editor-group}")
    private String groupId;

    // ═══════════════════════════════════════════
    //  PRODUCER
    // ═══════════════════════════════════════════

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Durability: wait for all in-sync replicas to acknowledge
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // Idempotent producer — prevents duplicates on retry
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Retry on transient failures (network blip, leader election)
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 500);

        // Batching for throughput: buffer up to 16 KB or 10 ms
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16_384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);

        // Compression reduces network/storage costs
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

        // JacksonJsonSerializer — no-arg constructor, includes __TypeId__ header by default
        // Use .noTypeInfo() if you want to disable type headers
        JacksonJsonSerializer<Object> valueSerializer = new JacksonJsonSerializer<>();

        return new DefaultKafkaProducerFactory<>(
                props,
                new StringSerializer(),
                valueSerializer
        );
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ═══════════════════════════════════════════
    //  CONSUMER
    // ═══════════════════════════════════════════

    @Bean
    public ConsumerFactory<String, DocumentOperationEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Performance tuning
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100);

        // JacksonJsonDeserializer — no-arg constructor with fluent API
        // .trustedPackages() controls which classes can be instantiated (security)
        JacksonJsonDeserializer<DocumentOperationEvent> valueDeserializer =
                new JacksonJsonDeserializer<>(DocumentOperationEvent.class)
                        .trustedPackages("com.collabeditor.dto.websocket");

        // Wrap with ErrorHandlingDeserializer for poison-pill protection
        ErrorHandlingDeserializer<DocumentOperationEvent> errorHandlingDeserializer =
                new ErrorHandlingDeserializer<>(valueDeserializer);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                errorHandlingDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DocumentOperationEvent>
    kafkaListenerContainerFactory(ConsumerFactory<String, DocumentOperationEvent> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, DocumentOperationEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Concurrency: number of consumer threads (match partition count / instances)
        factory.setConcurrency(3);

        // Error handling: retry 3 times with 1s backoff, then skip
        CommonErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> {
                    // Dead-letter logging — in production, push to a DLT topic
                    System.err.printf(
                            "Kafka listener exhausted retries for record key=%s topic=%s partition=%d offset=%d: %s%n",
                            record.key(), record.topic(), record.partition(), record.offset(),
                            exception.getMessage()
                    );
                },
                new FixedBackOff(1000L, 3L)  // 1 second interval, 3 attempts
        );
        factory.setCommonErrorHandler(errorHandler);

        // Process one record at a time for per-document ordering safety
        factory.setBatchListener(false);

        return factory;
    }
}
