package com.sporya.club.domain;

import java.util.UUID;

public class ClubNotFoundException extends RuntimeException {

  public ClubNotFoundException(UUID id) {
    super("Club not found: " + id);
  }
}
