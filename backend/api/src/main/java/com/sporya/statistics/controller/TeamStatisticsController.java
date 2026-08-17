package com.sporya.statistics.controller;

import com.sporya.statistics.application.TeamStatisticsService;
import com.sporya.statistics.controller.dto.TeamSeasonStatisticsResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teams/{teamId}/seasons/{seasonId}/statistics")
public class TeamStatisticsController {

  private final TeamStatisticsService teamStatisticsService;

  public TeamStatisticsController(TeamStatisticsService teamStatisticsService) {
    this.teamStatisticsService = teamStatisticsService;
  }

  @GetMapping
  public TeamSeasonStatisticsResponse stats(
      @PathVariable UUID teamId, @PathVariable UUID seasonId) {
    return teamStatisticsService.seasonStatsFor(teamId, seasonId);
  }
}
