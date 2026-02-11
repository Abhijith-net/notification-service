package com.example.notificationservice.messaging;

public interface NotificationProducer {

    void send(NotificationEvent event);
}
