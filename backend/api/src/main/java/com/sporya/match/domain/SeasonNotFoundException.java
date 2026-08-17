package com.sporya.match.domain;

import java.util.UUID;

public class SeasonNotFoundException extends RuntimeException {

  public SeasonNotFoundException(UUID id) {
    super("Season not found: " + id);
  }
}
