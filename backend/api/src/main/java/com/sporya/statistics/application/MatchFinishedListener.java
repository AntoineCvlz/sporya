package com.sporya.statistics.application;

import com.sporya.match.application.MatchEventService;
import com.sporya.match.controller.dto.MatchEventResponse;
import com.sporya.match.domain.MatchEventType;
import com.sporya.match.domain.MatchFinishedEvent;
import com.sporya.statistics.domain.MatchOutcome;
import com.sporya.statistics.domain.PlayerMatchStatistics;
import com.sporya.statistics.domain.TeamMatchStatistics;
import com.sporya.statistics.infrastructure.persistence.PlayerMatchStatisticsRepository;
import com.sporya.statistics.infrastructure.persistence.TeamMatchStatisticsRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MatchFinishedListener {

  private final MatchEventService matchEventService;
  private final PlayerMatchStatisticsRepository playerMatchStatisticsRepository;
  private final TeamMatchStatisticsRepository teamMatchStatisticsRepository;

  public MatchFinishedListener(
      MatchEventService matchEventService,
      PlayerMatchStatisticsRepository playerMatchStatisticsRepository,
      TeamMatchStatisticsRepository teamMatchStatisticsRepository) {
    this.matchEventService = matchEventService;
    this.playerMatchStatisticsRepository = playerMatchStatisticsRepository;
    this.teamMatchStatisticsRepository = teamMatchStatisticsRepository;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onMatchFinished(MatchFinishedEvent event) {
    List<MatchEventResponse> events = matchEventService.listForMatch(event.matchId());

    int homeGoals = countGoalsForTeam(events, event.homeTeamId());
    int awayGoals = countGoalsForTeam(events, event.awayTeamId());

    teamMatchStatisticsRepository.save(
        new TeamMatchStatistics(
            event.homeTeamId(),
            event.matchId(),
            event.seasonId(),
            homeGoals,
            awayGoals,
            outcomeFor(homeGoals, awayGoals)));
    teamMatchStatisticsRepository.save(
        new TeamMatchStatistics(
            event.awayTeamId(),
            event.matchId(),
            event.seasonId(),
            awayGoals,
            homeGoals,
            outcomeFor(awayGoals, homeGoals)));

    Map<UUID, List<MatchEventResponse>> eventsByPlayer =
        events.stream().collect(Collectors.groupingBy(MatchEventResponse::playerId));
    eventsByPlayer.forEach(
        (playerId, playerEvents) -> {
          UUID teamId = playerEvents.get(0).teamId();
          int goals = countByType(playerEvents, MatchEventType.GOAL_SCORED);
          int yellowCards = countByType(playerEvents, MatchEventType.YELLOW_CARD);
          int redCards = countByType(playerEvents, MatchEventType.RED_CARD);
          playerMatchStatisticsRepository.save(
              new PlayerMatchStatistics(
                  playerId,
                  event.matchId(),
                  teamId,
                  event.seasonId(),
                  goals,
                  yellowCards,
                  redCards));
        });
  }

  private static int countGoalsForTeam(List<MatchEventResponse> events, UUID teamId) {
    return (int)
        events.stream()
            .filter(e -> e.type() == MatchEventType.GOAL_SCORED && e.teamId().equals(teamId))
            .count();
  }

  private static int countByType(List<MatchEventResponse> events, MatchEventType type) {
    return (int) events.stream().filter(e -> e.type() == type).count();
  }

  private static MatchOutcome outcomeFor(int goalsFor, int goalsAgainst) {
    if (goalsFor == goalsAgainst) {
      return MatchOutcome.DRAW;
    }
    return goalsFor > goalsAgainst ? MatchOutcome.WIN : MatchOutcome.LOSS;
  }
}
