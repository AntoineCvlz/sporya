package com.sporya.club.controller.dto;

import com.sporya.club.domain.Club;
import java.time.Instant;
import java.util.UUID;

public record ClubResponse(UUID id, String name, String country, UUID createdBy, Instant createdAt) {

  public static ClubResponse from(Club club) {
    return new ClubResponse(
        club.getId(), club.getName(), club.getCountry(), club.getCreatedBy(), club.getCreatedAt());
  }
}
