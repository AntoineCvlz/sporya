package com.sporya.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "notification")
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private NotificationType type;

  @Column(name = "match_id", nullable = false)
  private UUID matchId;

  @Column(name = "home_team_id", nullable = false)
  private UUID homeTeamId;

  @Column(name = "away_team_id", nullable = false)
  private UUID awayTeamId;

  @Column(name = "home_score", nullable = false)
  private int homeScore;

  @Column(name = "away_score", nullable = false)
  private int awayScore;

  @Column(nullable = false)
  private boolean read;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Notification() {}

  public Notification(
      UUID userId,
      NotificationType type,
      UUID matchId,
      UUID homeTeamId,
      UUID awayTeamId,
      int homeScore,
      int awayScore) {
    this.userId = userId;
    this.type = type;
    this.matchId = matchId;
    this.homeTeamId = homeTeamId;
    this.awayTeamId = awayTeamId;
    this.homeScore = homeScore;
    this.awayScore = awayScore;
    this.read = false;
    this.createdAt = Instant.now();
  }

  public void markRead() {
    this.read = true;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public NotificationType getType() {
    return type;
  }

  public UUID getMatchId() {
    return matchId;
  }

  public UUID getHomeTeamId() {
    return homeTeamId;
  }

  public UUID getAwayTeamId() {
    return awayTeamId;
  }

  public int getHomeScore() {
    return homeScore;
  }

  public int getAwayScore() {
    return awayScore;
  }

  public boolean isRead() {
    return read;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
