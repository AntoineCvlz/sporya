package com.sporya.notification.application;

import com.sporya.notification.controller.dto.NotificationResponse;
import com.sporya.notification.domain.Notification;
import com.sporya.notification.domain.NotificationNotFoundException;
import com.sporya.notification.infrastructure.persistence.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;

  public NotificationService(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  @Transactional(readOnly = true)
  public List<NotificationResponse> listForUser(UUID userId) {
    return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(NotificationResponse::from)
        .toList();
  }

  @Transactional
  public NotificationResponse markRead(UUID userId, UUID notificationId) {
    Notification notification =
        notificationRepository
            .findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    notification.markRead();
    return NotificationResponse.from(notificationRepository.save(notification));
  }
}
