package com.example.notificationservice.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.queue.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpNotificationProducer implements NotificationProducer {

    @Override
    public void send(NotificationEvent event) {
        // No-op when queue is disabled; in-process sender handles delivery
    }
}
