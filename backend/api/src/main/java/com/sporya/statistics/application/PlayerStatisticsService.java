package com.sporya.statistics.application;

import com.sporya.statistics.controller.dto.PlayerSeasonStatisticsResponse;
import com.sporya.statistics.domain.PlayerMatchStatistics;
import com.sporya.statistics.infrastructure.persistence.PlayerMatchStatisticsRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerStatisticsService {

  private final PlayerMatchStatisticsRepository playerMatchStatisticsRepository;

  public PlayerStatisticsService(PlayerMatchStatisticsRepository playerMatchStatisticsRepository) {
    this.playerMatchStatisticsRepository = playerMatchStatisticsRepository;
  }

  @Transactional(readOnly = true)
  public PlayerSeasonStatisticsResponse seasonStatsFor(UUID playerId, UUID seasonId) {
    List<PlayerMatchStatistics> rows =
        playerMatchStatisticsRepository.findByPlayerIdAndSeasonId(playerId, seasonId);
    int goals = rows.stream().mapToInt(PlayerMatchStatistics::getGoals).sum();
    int yellowCards = rows.stream().mapToInt(PlayerMatchStatistics::getYellowCards).sum();
    int redCards = rows.stream().mapToInt(PlayerMatchStatistics::getRedCards).sum();
    return new PlayerSeasonStatisticsResponse(
        playerId, seasonId, goals, yellowCards, redCards, rows.size());
  }
}
