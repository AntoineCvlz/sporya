package com.sporya.notification.controller.dto;

import com.sporya.notification.domain.Notification;
import com.sporya.notification.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    NotificationType type,
    UUID matchId,
    UUID homeTeamId,
    UUID awayTeamId,
    int homeScore,
    int awayScore,
    boolean read,
    Instant createdAt) {

  public static NotificationResponse from(Notification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getType(),
        notification.getMatchId(),
        notification.getHomeTeamId(),
        notification.getAwayTeamId(),
        notification.getHomeScore(),
        notification.getAwayScore(),
        notification.isRead(),
        notification.getCreatedAt());
  }
}
