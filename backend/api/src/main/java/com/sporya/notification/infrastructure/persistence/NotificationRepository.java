package com.sporya.notification.infrastructure.persistence;

import com.sporya.notification.domain.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
}
