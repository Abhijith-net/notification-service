package com.example.notificationservice.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.queue.enabled", havingValue = "true")
public class KafkaNotificationProducer implements NotificationProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationProducer.class);

    private final String topic;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public KafkaNotificationProducer(
        @Value("${notification.kafka.topic:notification-send}") String topic,
        KafkaTemplate<String, NotificationEvent> kafkaTemplate) {
        this.topic = topic;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(NotificationEvent event) {
        try {
            kafkaTemplate.send(topic, event.notificationId().toString(), event);
            log.debug("Enqueued notification {}", event.notificationId());
        } catch (Exception e) {
            log.warn("Failed to enqueue notification {}: {}", event.notificationId(), e.getMessage());
        }
    }
}
