package com.sporya.match.controller.dto;

import com.sporya.match.domain.Match;
import com.sporya.match.domain.MatchStatus;
import java.time.Instant;
import java.util.UUID;

public record MatchResponse(
    UUID id,
    UUID seasonId,
    UUID homeTeamId,
    UUID awayTeamId,
    MatchStatus status,
    Instant kickoffAt,
    int homeScore,
    int awayScore,
    Instant createdAt) {

  public static MatchResponse from(Match match, int homeScore, int awayScore) {
    return new MatchResponse(
        match.getId(),
        match.getSeasonId(),
        match.getHomeTeamId(),
        match.getAwayTeamId(),
        match.getStatus(),
        match.getKickoffAt(),
        homeScore,
        awayScore,
        match.getCreatedAt());
  }
}
