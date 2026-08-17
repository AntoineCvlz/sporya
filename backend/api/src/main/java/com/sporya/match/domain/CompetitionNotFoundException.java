package com.sporya.match.domain;

import java.util.UUID;

public class CompetitionNotFoundException extends RuntimeException {

  public CompetitionNotFoundException(UUID id) {
    super("Competition not found: " + id);
  }
}
