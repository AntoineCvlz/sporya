package com.sporya.club.controller.dto;

import com.sporya.club.domain.Player;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PlayerResponse(
    UUID id, String name, LocalDate birthdate, String position, UUID teamId, Instant createdAt) {

  public static PlayerResponse from(Player player) {
    return new PlayerResponse(
        player.getId(),
        player.getName(),
        player.getBirthdate(),
        player.getPosition(),
        player.getTeamId(),
        player.getCreatedAt());
  }
}
