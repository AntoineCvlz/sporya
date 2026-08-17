package com.sporya.notification.domain;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {

  public NotificationNotFoundException(UUID id) {
    super("Notification not found: " + id);
  }
}
