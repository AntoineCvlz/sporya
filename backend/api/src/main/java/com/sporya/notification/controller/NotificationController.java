package com.sporya.notification.controller;

import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.notification.application.NotificationService;
import com.sporya.notification.controller.dto.NotificationResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  public List<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser caller) {
    return notificationService.listForUser(caller.userId());
  }

  @PostMapping("/{notificationId}/read")
  public NotificationResponse markRead(
      @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID notificationId) {
    return notificationService.markRead(caller.userId(), notificationId);
  }
}
