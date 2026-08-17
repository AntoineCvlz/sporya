package com.sporya.match.controller.dto;

import com.sporya.match.domain.Competition;
import java.time.Instant;
import java.util.UUID;

public record CompetitionResponse(UUID id, String name, Instant createdAt) {

  public static CompetitionResponse from(Competition competition) {
    return new CompetitionResponse(
        competition.getId(), competition.getName(), competition.getCreatedAt());
  }
}
