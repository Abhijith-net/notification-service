package com.example.notificationservice.channel.whatsapp;

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
 * WhatsApp via Meta Cloud API (dummy token for development).
 */
@Component
@ConditionalOnProperty(name = "notification.channels.whatsapp.enabled", havingValue = "true", matchIfMissing = true)
public class WhatsAppChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppChannel.class);

    @Value("${notification.channels.whatsapp.access-token:dummy-whatsapp-access-token}")
    private String accessToken;

    @Value("${notification.channels.whatsapp.phone-number-id:123456789}")
    private String phoneNumberId;

    @Value("${notification.channels.whatsapp.api-url:https://graph.facebook.com/v18.0}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public ChannelType getChannelType() {
        return ChannelType.WHATSAPP;
    }

    @Override
    public CompletableFuture<SendResult> send(NotificationPayload payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = apiUrl + "/" + phoneNumberId + "/messages";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(accessToken);
                Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "to", payload.recipient().replaceAll("[^0-9]", ""),
                    "type", "text",
                    "text", Map.of("body", payload.body())
                );
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    String externalId = "wa-" + payload.notificationId();
                    log.debug("WhatsApp sent to {} for notification {}", payload.recipient(), payload.notificationId());
                    return SendResult.ok(externalId);
                } else {
                    return SendResult.failure("WhatsApp API returned " + response.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("WhatsApp send failed for {}: {}", payload.notificationId(), e.getMessage());
                return SendResult.failure(e.getMessage());
            }
        });
    }
}
