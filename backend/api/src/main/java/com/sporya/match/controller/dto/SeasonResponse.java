package com.sporya.match.controller.dto;

import com.sporya.match.domain.Season;
import java.time.Instant;
import java.util.UUID;

public record SeasonResponse(UUID id, String label, UUID competitionId, Instant createdAt) {

  public static SeasonResponse from(Season season) {
    return new SeasonResponse(
        season.getId(), season.getLabel(), season.getCompetitionId(), season.getCreatedAt());
  }
}
