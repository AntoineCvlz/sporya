package com.sporya.club.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTeamRequest(@NotBlank String name) {}
