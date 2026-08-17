package com.sporya.match.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seasons", schema = "match")
public class Season {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String label;

  @Column(name = "competition_id", nullable = false)
  private UUID competitionId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Season() {}

  public Season(String label, UUID competitionId) {
    this.label = label;
    this.competitionId = competitionId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getLabel() {
    return label;
  }

  public UUID getCompetitionId() {
    return competitionId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
