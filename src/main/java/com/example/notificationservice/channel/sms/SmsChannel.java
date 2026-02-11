package com.example.notificationservice.channel.sms;

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

import java.util.concurrent.CompletableFuture;

/**
 * SMS via Twilio-style API (dummy keys for development).
 */
@Component
@ConditionalOnProperty(name = "notification.channels.sms.enabled", havingValue = "true", matchIfMissing = true)
public class SmsChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsChannel.class);

    @Value("${notification.channels.sms.account-sid:ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx}")
    private String accountSid;

    @Value("${notification.channels.sms.auth-token:dummy-auth-token}")
    private String authToken;

    @Value("${notification.channels.sms.from-number:+15551234567}")
    private String fromNumber;

    @Value("${notification.channels.sms.api-url:https://api.twilio.com/2010-04-01/Accounts}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public ChannelType getChannelType() {
        return ChannelType.SMS;
    }

    @Override
    public CompletableFuture<SendResult> send(NotificationPayload payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = apiUrl + "/" + accountSid + "/Messages.json";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                headers.setBasicAuth(accountSid, authToken);
                String body = "To=" + payload.recipient() + "&From=" + fromNumber + "&Body=" + payload.body();
                HttpEntity<String> entity = new HttpEntity<>(body, headers);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    String externalId = "sms-" + payload.notificationId();
                    log.debug("SMS sent to {} for notification {}", payload.recipient(), payload.notificationId());
                    return SendResult.ok(externalId);
                } else {
                    return SendResult.failure("SMS API returned " + response.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("SMS send failed for {}: {}", payload.notificationId(), e.getMessage());
                return SendResult.failure(e.getMessage());
            }
        });
    }
}
