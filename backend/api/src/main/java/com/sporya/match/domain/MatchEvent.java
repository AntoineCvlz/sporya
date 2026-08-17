package com.sporya.match.domain;

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
@Table(name = "match_events", schema = "match")
public class MatchEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "match_id", nullable = false)
  private UUID matchId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MatchEventType type;

  @Column(nullable = false)
  private int minute;

  @Column(name = "player_id", nullable = false)
  private UUID playerId;

  @Column(name = "team_id", nullable = false)
  private UUID teamId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MatchEvent() {}

  public MatchEvent(UUID matchId, MatchEventType type, int minute, UUID playerId, UUID teamId) {
    this.matchId = matchId;
    this.type = type;
    this.minute = minute;
    this.playerId = playerId;
    this.teamId = teamId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getMatchId() {
    return matchId;
  }

  public MatchEventType getType() {
    return type;
  }

  public int getMinute() {
    return minute;
  }

  public UUID getPlayerId() {
    return playerId;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
