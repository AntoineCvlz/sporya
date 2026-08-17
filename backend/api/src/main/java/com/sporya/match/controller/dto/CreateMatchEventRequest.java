package com.sporya.match.controller.dto;

import com.sporya.match.domain.MatchEventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateMatchEventRequest(
    @NotNull MatchEventType type, @Min(0) int minute, @NotNull UUID playerId) {}
