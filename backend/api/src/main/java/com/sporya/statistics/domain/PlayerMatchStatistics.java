package com.sporya.statistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_match_statistics", schema = "statistics")
public class PlayerMatchStatistics {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "player_id", nullable = false)
  private UUID playerId;

  @Column(name = "match_id", nullable = false)
  private UUID matchId;

  @Column(name = "team_id", nullable = false)
  private UUID teamId;

  @Column(name = "season_id", nullable = false)
  private UUID seasonId;

  @Column(nullable = false)
  private int goals;

  @Column(name = "yellow_cards", nullable = false)
  private int yellowCards;

  @Column(name = "red_cards", nullable = false)
  private int redCards;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PlayerMatchStatistics() {}

  public PlayerMatchStatistics(
      UUID playerId,
      UUID matchId,
      UUID teamId,
      UUID seasonId,
      int goals,
      int yellowCards,
      int redCards) {
    this.playerId = playerId;
    this.matchId = matchId;
    this.teamId = teamId;
    this.seasonId = seasonId;
    this.goals = goals;
    this.yellowCards = yellowCards;
    this.redCards = redCards;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getPlayerId() {
    return playerId;
  }

  public UUID getMatchId() {
    return matchId;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public UUID getSeasonId() {
    return seasonId;
  }

  public int getGoals() {
    return goals;
  }

  public int getYellowCards() {
    return yellowCards;
  }

  public int getRedCards() {
    return redCards;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
