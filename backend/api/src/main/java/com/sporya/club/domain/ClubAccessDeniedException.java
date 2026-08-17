package com.sporya.club.domain;

import java.util.UUID;

public class ClubAccessDeniedException extends RuntimeException {

  public ClubAccessDeniedException(UUID clubId) {
    super("Not authorized to manage members of club: " + clubId);
  }
}
