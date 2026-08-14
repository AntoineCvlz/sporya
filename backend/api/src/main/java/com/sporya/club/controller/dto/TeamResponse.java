package com.sporya.club.controller.dto;

import com.sporya.club.domain.Team;
import java.time.Instant;
import java.util.UUID;

public record TeamResponse(UUID id, String name, UUID clubId, Instant createdAt) {

  public static TeamResponse from(Team team) {
    return new TeamResponse(team.getId(), team.getName(), team.getClubId(), team.getCreatedAt());
  }
}
