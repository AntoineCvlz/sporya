package com.sporya.statistics.controller.dto;

import java.util.UUID;

public record TeamSeasonStatisticsResponse(
    UUID teamId, UUID seasonId, int wins, int draws, int losses, int goalsFor, int goalsAgainst) {}
