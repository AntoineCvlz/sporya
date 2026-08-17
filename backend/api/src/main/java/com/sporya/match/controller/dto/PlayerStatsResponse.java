package com.sporya.match.controller.dto;

import java.util.UUID;

public record PlayerStatsResponse(
    UUID playerId, long goals, long yellowCards, long redCards, long matchesPlayed) {}
