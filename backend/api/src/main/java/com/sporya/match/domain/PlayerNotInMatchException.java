package com.sporya.match.domain;

import java.util.UUID;

public class PlayerNotInMatchException extends RuntimeException {

  public PlayerNotInMatchException(UUID playerId, UUID matchId) {
    super("Player " + playerId + " is not part of match " + matchId);
  }
}
