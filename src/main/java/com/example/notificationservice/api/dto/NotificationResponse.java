package com.example.notificationservice.api.dto;

import java.util.UUID;

public class NotificationResponse {

    private UUID notificationId;
    private String status;

    public NotificationResponse() {}

    public NotificationResponse(UUID notificationId, String status) {
        this.notificationId = notificationId;
        this.status = status;
    }

    public UUID getNotificationId() { return notificationId; }
    public void setNotificationId(UUID notificationId) { this.notificationId = notificationId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
