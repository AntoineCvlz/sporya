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
@Table(name = "matches", schema = "match")
public class Match {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "season_id", nullable = false)
  private UUID seasonId;

  @Column(name = "home_team_id", nullable = false)
  private UUID homeTeamId;

  @Column(name = "away_team_id", nullable = false)
  private UUID awayTeamId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MatchStatus status;

  @Column(name = "kickoff_at", nullable = false)
  private Instant kickoffAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Match() {}

  public Match(UUID seasonId, UUID homeTeamId, UUID awayTeamId, Instant kickoffAt) {
    this.seasonId = seasonId;
    this.homeTeamId = homeTeamId;
    this.awayTeamId = awayTeamId;
    this.status = MatchStatus.SCHEDULED;
    this.kickoffAt = kickoffAt;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getSeasonId() {
    return seasonId;
  }

  public UUID getHomeTeamId() {
    return homeTeamId;
  }

  public UUID getAwayTeamId() {
    return awayTeamId;
  }

  public MatchStatus getStatus() {
    return status;
  }

  public void setStatus(MatchStatus status) {
    this.status = status;
  }

  public Instant getKickoffAt() {
    return kickoffAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
