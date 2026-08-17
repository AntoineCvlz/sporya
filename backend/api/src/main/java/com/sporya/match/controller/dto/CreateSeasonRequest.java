package com.sporya.match.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSeasonRequest(@NotBlank String label) {}
