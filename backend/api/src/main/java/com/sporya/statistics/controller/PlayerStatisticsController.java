package com.sporya.statistics.controller;

import com.sporya.statistics.application.PlayerStatisticsService;
import com.sporya.statistics.controller.dto.PlayerSeasonStatisticsResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/players/{playerId}/seasons/{seasonId}/statistics")
public class PlayerStatisticsController {

  private final PlayerStatisticsService playerStatisticsService;

  public PlayerStatisticsController(PlayerStatisticsService playerStatisticsService) {
    this.playerStatisticsService = playerStatisticsService;
  }

  @GetMapping
  public PlayerSeasonStatisticsResponse stats(
      @PathVariable UUID playerId, @PathVariable UUID seasonId) {
    return playerStatisticsService.seasonStatsFor(playerId, seasonId);
  }
}
