package com.sporya.club.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreatePlayerRequest(
    @NotBlank String name, @NotNull LocalDate birthdate, @NotBlank String position) {}
