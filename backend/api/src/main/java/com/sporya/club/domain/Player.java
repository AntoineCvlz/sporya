package com.sporya.club.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "players", schema = "club")
public class Player {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private LocalDate birthdate;

  @Column(nullable = false)
  private String position;

  @Column(name = "team_id", nullable = false)
  private UUID teamId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Player() {}

  public Player(String name, LocalDate birthdate, String position, UUID teamId) {
    this.name = name;
    this.birthdate = birthdate;
    this.position = position;
    this.teamId = teamId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public LocalDate getBirthdate() {
    return birthdate;
  }

  public String getPosition() {
    return position;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
