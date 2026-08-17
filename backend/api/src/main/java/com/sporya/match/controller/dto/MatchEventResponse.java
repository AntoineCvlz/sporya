package com.sporya.match.controller.dto;

import com.sporya.match.domain.MatchEvent;
import com.sporya.match.domain.MatchEventType;
import java.time.Instant;
import java.util.UUID;

public record MatchEventResponse(
    UUID id,
    UUID matchId,
    MatchEventType type,
    int minute,
    UUID playerId,
    UUID teamId,
    Instant createdAt) {

  public static MatchEventResponse from(MatchEvent event) {
    return new MatchEventResponse(
        event.getId(),
        event.getMatchId(),
        event.getType(),
        event.getMinute(),
        event.getPlayerId(),
        event.getTeamId(),
        event.getCreatedAt());
  }
}
