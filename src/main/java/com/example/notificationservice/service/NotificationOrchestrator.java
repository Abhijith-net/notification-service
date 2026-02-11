package com.example.notificationservice.service;

import com.example.notificationservice.api.dto.NotificationRequest;
import com.example.notificationservice.api.dto.RecipientDto;
import com.example.notificationservice.channel.NotificationPayload;
import com.example.notificationservice.domain.ChannelType;
import com.example.notificationservice.domain.Notification;
import com.example.notificationservice.domain.NotificationStatus;
import com.example.notificationservice.messaging.NotificationEvent;
import com.example.notificationservice.messaging.NotificationProducer;
import com.example.notificationservice.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationOrchestrator {

    private final TemplateService templateService;
    private final NotificationRepository notificationRepository;
    private final NotificationProducer notificationProducer;
    private final InProcessNotificationSender inProcessSender;

    @Value("${notification.queue.enabled:false}")
    private boolean queueEnabled;

    public NotificationOrchestrator(TemplateService templateService,
                                   NotificationRepository notificationRepository,
                                   NotificationProducer notificationProducer,
                                   @Lazy InProcessNotificationSender inProcessSender) {
        this.templateService = templateService;
        this.notificationRepository = notificationRepository;
        this.notificationProducer = notificationProducer;
        this.inProcessSender = inProcessSender;
    }

    @Transactional
    public List<Notification> accept(NotificationRequest request) {
        List<Notification> created = new ArrayList<>();
        Map<String, String> variables = request.getVariables() != null ? request.getVariables() : Map.of();

        for (ChannelType channel : request.getChannels()) {
            for (RecipientDto recipient : request.getRecipients()) {
                if (recipient.getChannel() != channel) continue;
                ResolvedContent content = resolveContent(request.getTemplateId(), channel, variables);
                Notification n = createNotification(
                    request.getTemplateId(),
                    channel,
                    recipient.getAddress(),
                    variables,
                    request.getPriority(),
                    content
                );
                created.add(notificationRepository.save(n));
            }
        }

        for (Notification n : created) {
            if (queueEnabled) {
                notificationProducer.send(NotificationEvent.from(n));
            } else {
                inProcessSender.sendAsync(n);
            }
            n.setStatus(NotificationStatus.ACCEPTED);
        }
        notificationRepository.saveAll(created);
        return created;
    }

    private ResolvedContent resolveContent(String templateId, ChannelType channel, Map<String, String> variables) {
        try {
            TemplateService.ResolvedTemplate rt = templateService.resolve(templateId, channel, "en", variables);
            return new ResolvedContent(rt.subject(), rt.body());
        } catch (Exception e) {
            throw new IllegalArgumentException("Template resolution failed: " + e.getMessage());
        }
    }

    private Notification createNotification(String templateId, ChannelType channel, String recipient,
                                            Map<String, String> variables, String priority, ResolvedContent content) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setTemplateId(templateId);
        n.setChannel(channel);
        n.setRecipient(recipient);
        n.setVariables(variables);
        n.setPriority(priority);
        n.setStatus(NotificationStatus.PENDING);
        n.setSubject(content.subject);
        n.setBody(content.body);
        n.setRetryCount(0);
        return n;
    }

    public NotificationPayload toPayload(Notification n) {
        return new NotificationPayload(
            n.getId(),
            n.getChannel(),
            n.getRecipient(),
            n.getSubject(),
            n.getBody(),
            n.getRetryCount()
        );
    }

    private record ResolvedContent(String subject, String body) {}
}
