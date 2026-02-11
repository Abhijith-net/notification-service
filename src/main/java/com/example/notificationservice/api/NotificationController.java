package com.example.notificationservice.api;

import com.example.notificationservice.api.dto.NotificationRequest;
import com.example.notificationservice.api.dto.NotificationResponse;
import com.example.notificationservice.domain.Notification;
import com.example.notificationservice.domain.NotificationStatus;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.service.NotificationOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationOrchestrator orchestrator;
    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationOrchestrator orchestrator,
                                  NotificationRepository notificationRepository) {
        this.orchestrator = orchestrator;
        this.notificationRepository = notificationRepository;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> create(@Valid @RequestBody NotificationRequest request,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        try {
            List<Notification> created = orchestrator.accept(request);
            if (created.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new NotificationResponse(null, "INVALID"));
            }
            Notification first = created.get(0);
            return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new NotificationResponse(first.getId(), NotificationStatus.ACCEPTED.name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(new NotificationResponse(null, "INVALID"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new NotificationResponse(null, "ERROR"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationStatusDto> get(@PathVariable UUID id) {
        return notificationRepository.findById(id)
            .map(n -> ResponseEntity.ok(new NotificationStatusDto(
                n.getId(),
                n.getStatus().name(),
                n.getChannel().name(),
                n.getRecipient(),
                n.getSentAt(),
                n.getErrorMessage())))
            .orElse(ResponseEntity.notFound().build());
    }

    public record NotificationStatusDto(
        UUID notificationId,
        String status,
        String channel,
        String recipient,
        java.time.Instant sentAt,
        String errorMessage
    ) {}
}
