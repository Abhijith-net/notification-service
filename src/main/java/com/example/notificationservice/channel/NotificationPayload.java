package com.example.notificationservice.channel;

import com.example.notificationservice.domain.ChannelType;

import java.util.UUID;

public record NotificationPayload(
    UUID notificationId,
    ChannelType channel,
    String recipient,
    String subject,
    String body,
    int retryCount
) {}
