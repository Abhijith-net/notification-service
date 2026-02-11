package com.example.notificationservice.repository;

import com.example.notificationservice.domain.Notification;
import com.example.notificationservice.domain.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByStatus(NotificationStatus status);
}
