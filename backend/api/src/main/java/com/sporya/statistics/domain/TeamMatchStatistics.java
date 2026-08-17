package com.sporya.statistics.domain;

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
@Table(name = "team_match_statistics", schema = "statistics")
public class TeamMatchStatistics {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "team_id", nullable = false)
  private UUID teamId;

  @Column(name = "match_id", nullable = false)
  private UUID matchId;

  @Column(name = "season_id", nullable = false)
  private UUID seasonId;

  @Column(name = "goals_for", nullable = false)
  private int goalsFor;

  @Column(name = "goals_against", nullable = false)
  private int goalsAgainst;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private MatchOutcome result;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected TeamMatchStatistics() {}

  public TeamMatchStatistics(
      UUID teamId,
      UUID matchId,
      UUID seasonId,
      int goalsFor,
      int goalsAgainst,
      MatchOutcome result) {
    this.teamId = teamId;
    this.matchId = matchId;
    this.seasonId = seasonId;
    this.goalsFor = goalsFor;
    this.goalsAgainst = goalsAgainst;
    this.result = result;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public UUID getMatchId() {
    return matchId;
  }

  public UUID getSeasonId() {
    return seasonId;
  }

  public int getGoalsFor() {
    return goalsFor;
  }

  public int getGoalsAgainst() {
    return goalsAgainst;
  }

  public MatchOutcome getResult() {
    return result;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
