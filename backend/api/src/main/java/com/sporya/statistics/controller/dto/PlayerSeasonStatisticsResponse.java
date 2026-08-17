package com.sporya.statistics.controller.dto;

import java.util.UUID;

public record PlayerSeasonStatisticsResponse(
    UUID playerId, UUID seasonId, int goals, int yellowCards, int redCards, int matchesPlayed) {}
