package com.example.notificationservice.channel;

import com.example.notificationservice.domain.ChannelType;

import java.util.concurrent.CompletableFuture;

/**
 * Pluggable notification channel. Implementations are registered in ChannelRegistry.
 */
public interface NotificationChannel {

    ChannelType getChannelType();

    /**
     * Send the notification. Returns a future with success/failure and optional external id.
     */
    CompletableFuture<SendResult> send(NotificationPayload payload);

    /**
     * Whether this channel is enabled (e.g. via configuration).
     */
    default boolean isEnabled() {
        return true;
    }
}
