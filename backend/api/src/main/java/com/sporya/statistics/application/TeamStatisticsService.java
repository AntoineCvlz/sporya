package com.sporya.statistics.application;

import com.sporya.statistics.controller.dto.TeamSeasonStatisticsResponse;
import com.sporya.statistics.domain.MatchOutcome;
import com.sporya.statistics.domain.TeamMatchStatistics;
import com.sporya.statistics.infrastructure.persistence.TeamMatchStatisticsRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamStatisticsService {

  private final TeamMatchStatisticsRepository teamMatchStatisticsRepository;

  public TeamStatisticsService(TeamMatchStatisticsRepository teamMatchStatisticsRepository) {
    this.teamMatchStatisticsRepository = teamMatchStatisticsRepository;
  }

  @Transactional(readOnly = true)
  public TeamSeasonStatisticsResponse seasonStatsFor(UUID teamId, UUID seasonId) {
    List<TeamMatchStatistics> rows =
        teamMatchStatisticsRepository.findByTeamIdAndSeasonId(teamId, seasonId);
    int wins = (int) rows.stream().filter(row -> row.getResult() == MatchOutcome.WIN).count();
    int draws = (int) rows.stream().filter(row -> row.getResult() == MatchOutcome.DRAW).count();
    int losses = (int) rows.stream().filter(row -> row.getResult() == MatchOutcome.LOSS).count();
    int goalsFor = rows.stream().mapToInt(TeamMatchStatistics::getGoalsFor).sum();
    int goalsAgainst = rows.stream().mapToInt(TeamMatchStatistics::getGoalsAgainst).sum();
    return new TeamSeasonStatisticsResponse(
        teamId, seasonId, wins, draws, losses, goalsFor, goalsAgainst);
  }
}
