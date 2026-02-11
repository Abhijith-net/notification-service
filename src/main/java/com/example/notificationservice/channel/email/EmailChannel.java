package com.example.notificationservice.channel.email;

import com.example.notificationservice.channel.NotificationChannel;
import com.example.notificationservice.channel.NotificationPayload;
import com.example.notificationservice.channel.SendResult;
import com.example.notificationservice.domain.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(name = "notification.channels.email.enabled", havingValue = "true", matchIfMissing = true)
public class EmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    @Value("${notification.channels.email.from:dummy-noreply@example.com}")
    private String fromAddress;

    private final JavaMailSender mailSender;

    public EmailChannel(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.EMAIL;
    }

    @Override
    public CompletableFuture<SendResult> send(NotificationPayload payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromAddress);
                msg.setTo(payload.recipient());
                msg.setSubject(payload.subject() != null ? payload.subject() : "(No subject)");
                msg.setText(payload.body());
                mailSender.send(msg);
                String externalId = "email-" + payload.notificationId();
                log.debug("Email sent to {} for notification {}", payload.recipient(), payload.notificationId());
                return SendResult.ok(externalId);
            } catch (Exception e) {
                log.warn("Email send failed for {}: {}", payload.notificationId(), e.getMessage());
                return SendResult.failure(e.getMessage());
            }
        });
    }
}
