package com.sporya.match.controller;

import com.sporya.match.application.MatchEventService;
import com.sporya.match.controller.dto.PlayerStatsResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerStatsController {

  private final MatchEventService matchEventService;

  public PlayerStatsController(MatchEventService matchEventService) {
    this.matchEventService = matchEventService;
  }

  @GetMapping("/{playerId}/stats")
  public PlayerStatsResponse stats(@PathVariable UUID playerId) {
    return matchEventService.statsFor(playerId);
  }
}
