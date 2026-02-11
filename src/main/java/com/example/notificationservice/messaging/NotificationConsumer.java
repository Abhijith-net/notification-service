package com.example.notificationservice.messaging;

import com.example.notificationservice.channel.ChannelRegistry;
import com.example.notificationservice.channel.NotificationChannel;
import com.example.notificationservice.channel.SendResult;
import com.example.notificationservice.domain.Notification;
import com.example.notificationservice.domain.NotificationStatus;
import com.example.notificationservice.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(name = "notification.queue.enabled", havingValue = "true")
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    @org.springframework.beans.factory.annotation.Value("${notification.consumer.max-retries:3}")
    private int maxRetries;

    private final NotificationRepository notificationRepository;
    private final ChannelRegistry channelRegistry;

    public NotificationConsumer(NotificationRepository notificationRepository, ChannelRegistry channelRegistry) {
        this.notificationRepository = notificationRepository;
        this.channelRegistry = channelRegistry;
    }

    @KafkaListener(topics = "${notification.kafka.topic:notification-send}", groupId = "${notification.kafka.consumer-group:notification-service}")
    @Transactional
    public void consume(NotificationEvent event) {
        Optional<Notification> opt = notificationRepository.findById(event.notificationId());
        if (opt.isEmpty()) {
            log.warn("Notification not found: {}", event.notificationId());
            return;
        }
        Notification n = opt.get();
        if (n.getStatus() == NotificationStatus.SENT) {
            log.debug("Already sent: {}", n.getId());
            return;
        }

        Optional<NotificationChannel> channelOpt = channelRegistry.getChannel(event.channel());
        if (channelOpt.isEmpty()) {
            fail(n, "Channel not supported: " + event.channel());
            return;
        }

        try {
            CompletableFuture<SendResult> future = channelOpt.get().send(
                new com.example.notificationservice.channel.NotificationPayload(
                    event.notificationId(),
                    event.channel(),
                    event.recipient(),
                    event.subject(),
                    event.body(),
                    event.retryCount()
                )
            );
            SendResult result = future.get();
            if (result.success()) {
                n.setStatus(NotificationStatus.SENT);
                n.setSentAt(Instant.now());
                n.setExternalId(result.externalId());
                n.setErrorMessage(null);
                notificationRepository.save(n);
                log.info("Sent notification {} via {}", n.getId(), event.channel());
            } else {
                handleFailure(n, result.errorMessage());
            }
        } catch (Exception e) {
            log.error("Send failed for {}: {}", n.getId(), e.getMessage());
            handleFailure(n, e.getMessage());
        }
    }

    private void handleFailure(Notification n, String errorMessage) {
        n.setRetryCount(n.getRetryCount() + 1);
        n.setErrorMessage(errorMessage);
        if (n.getRetryCount() >= maxRetries) {
            fail(n, errorMessage);
        } else {
            n.setStatus(NotificationStatus.PENDING);
            notificationRepository.save(n);
            log.warn("Notification {} failed (retry {}/{}): {}", n.getId(), n.getRetryCount(), maxRetries, errorMessage);
        }
    }

    private void fail(Notification n, String errorMessage) {
        n.setStatus(NotificationStatus.FAILED);
        n.setErrorMessage(errorMessage);
        notificationRepository.save(n);
        log.error("Notification {} marked FAILED: {}", n.getId(), errorMessage);
    }
}
