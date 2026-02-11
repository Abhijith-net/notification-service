package com.example.notificationservice.messaging;

import com.example.notificationservice.domain.ChannelType;
import com.example.notificationservice.domain.Notification;

import java.util.UUID;

public record NotificationEvent(
    UUID notificationId,
    ChannelType channel,
    String recipient,
    String subject,
    String body,
    int retryCount
) {
    public static NotificationEvent from(Notification n) {
        return new NotificationEvent(
            n.getId(),
            n.getChannel(),
            n.getRecipient(),
            n.getSubject(),
            n.getBody(),
            n.getRetryCount()
        );
    }
}
