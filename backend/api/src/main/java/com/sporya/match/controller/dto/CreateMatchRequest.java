package com.sporya.match.controller.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateMatchRequest(
    @NotNull UUID seasonId,
    @NotNull UUID homeTeamId,
    @NotNull UUID awayTeamId,
    @NotNull Instant kickoffAt) {}
