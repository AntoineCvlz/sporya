package com.sporya.match.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCompetitionRequest(@NotBlank String name) {}
