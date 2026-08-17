package com.sporya.match.domain;

import java.util.UUID;

public record MatchFinishedEvent(UUID matchId, UUID homeTeamId, UUID awayTeamId, UUID seasonId) {}
