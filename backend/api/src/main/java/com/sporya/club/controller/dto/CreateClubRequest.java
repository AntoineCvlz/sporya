package com.sporya.club.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateClubRequest(@NotBlank String name, @NotBlank String country) {}
