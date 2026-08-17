package com.sporya.match.controller.dto;

import com.sporya.match.domain.MatchResult;
import java.time.Instant;
import java.util.UUID;

public record RecentMatchResult(
    UUID matchId,
    MatchResult result,
    UUID opponentTeamId,
    int homeScore,
    int awayScore,
    Instant kickoffAt) {}
