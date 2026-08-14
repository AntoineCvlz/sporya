package com.sporya.club.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teams", schema = "club")
public class Team {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "club_id", nullable = false)
  private UUID clubId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Team() {}

  public Team(String name, UUID clubId) {
    this.name = name;
    this.clubId = clubId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public UUID getClubId() {
    return clubId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
