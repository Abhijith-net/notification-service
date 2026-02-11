package com.example.notificationservice.service;

import com.example.notificationservice.channel.ChannelRegistry;
import com.example.notificationservice.channel.NotificationChannel;
import com.example.notificationservice.channel.SendResult;
import com.example.notificationservice.domain.Notification;
import com.example.notificationservice.domain.NotificationStatus;
import com.example.notificationservice.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * When Kafka is disabled, sends notifications in-process asynchronously.
 */
@Component
public class InProcessNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(InProcessNotificationSender.class);

    private final NotificationRepository notificationRepository;
    private final ChannelRegistry channelRegistry;

    public InProcessNotificationSender(NotificationRepository notificationRepository,
                                       ChannelRegistry channelRegistry) {
        this.notificationRepository = notificationRepository;
        this.channelRegistry = channelRegistry;
    }

    @Async
    public void sendAsync(Notification n) {
        Optional<NotificationChannel> channelOpt = channelRegistry.getChannel(n.getChannel());
        if (channelOpt.isEmpty()) {
            fail(n, "Channel not supported: " + n.getChannel());
            return;
        }
        CompletableFuture<SendResult> future = channelOpt.get().send(
            new com.example.notificationservice.channel.NotificationPayload(
                n.getId(),
                n.getChannel(),
                n.getRecipient(),
                n.getSubject(),
                n.getBody(),
                n.getRetryCount()
            )
        );
        future.thenAccept(result -> updateNotification(n.getId(), result));
    }

    @Transactional
    public void updateNotification(java.util.UUID id, SendResult result) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (result.success()) {
                n.setStatus(NotificationStatus.SENT);
                n.setSentAt(Instant.now());
                n.setExternalId(result.externalId());
                n.setErrorMessage(null);
            } else {
                n.setStatus(NotificationStatus.FAILED);
                n.setErrorMessage(result.errorMessage());
            }
            notificationRepository.save(n);
            log.info("Notification {} updated to {}", n.getId(), n.getStatus());
        });
    }

    private void fail(Notification n, String errorMessage) {
        n.setStatus(NotificationStatus.FAILED);
        n.setErrorMessage(errorMessage);
        notificationRepository.save(n);
    }
}
