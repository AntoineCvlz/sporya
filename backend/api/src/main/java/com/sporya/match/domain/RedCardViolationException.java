package com.sporya.match.domain;

import java.util.UUID;

public class RedCardViolationException extends RuntimeException {

  public RedCardViolationException(UUID playerId, UUID matchId) {
    super("Player " + playerId + " already has a red card in match " + matchId + ", cannot score");
  }
}
