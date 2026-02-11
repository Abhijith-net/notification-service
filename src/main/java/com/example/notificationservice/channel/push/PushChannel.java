package com.example.notificationservice.channel.push;

import com.example.notificationservice.channel.NotificationChannel;
import com.example.notificationservice.channel.NotificationPayload;
import com.example.notificationservice.channel.SendResult;
import com.example.notificationservice.domain.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Push notifications via FCM (Firebase Cloud Messaging). Uses dummy server key for development.
 */
@Component
@ConditionalOnProperty(name = "notification.channels.push.enabled", havingValue = "true", matchIfMissing = true)
public class PushChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(PushChannel.class);

    @Value("${notification.channels.push.fcm-server-key:dummy-fcm-server-key}")
    private String fcmServerKey;

    @Value("${notification.channels.push.fcm-url:https://fcm.googleapis.com/fcm/send}")
    private String fcmUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public ChannelType getChannelType() {
        return ChannelType.PUSH;
    }

    @Override
    public CompletableFuture<SendResult> send(NotificationPayload payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "key=" + fcmServerKey);
                Map<String, Object> body = Map.of(
                    "to", payload.recipient(),
                    "notification", Map.of(
                        "title", payload.subject() != null ? payload.subject() : "Notification",
                        "body", payload.body()
                    )
                );
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<String> response = restTemplate.exchange(fcmUrl, HttpMethod.POST, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    String externalId = "push-" + payload.notificationId();
                    log.debug("Push sent to {} for notification {}", payload.recipient(), payload.notificationId());
                    return SendResult.ok(externalId);
                } else {
                    return SendResult.failure("FCM returned " + response.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("Push send failed for {}: {}", payload.notificationId(), e.getMessage());
                return SendResult.failure(e.getMessage());
            }
        });
    }
}
