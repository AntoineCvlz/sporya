package com.sporya.club.domain;

import java.util.UUID;

public class PlayerNotFoundException extends RuntimeException {

  public PlayerNotFoundException(UUID id) {
    super("Player not found: " + id);
  }
}
