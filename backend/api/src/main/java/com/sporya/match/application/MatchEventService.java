package com.sporya.match.application;

import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.club.application.PlayerService;
import com.sporya.club.application.TeamService;
import com.sporya.match.controller.dto.CreateMatchEventRequest;
import com.sporya.match.controller.dto.MatchEventResponse;
import com.sporya.match.domain.InvalidMatchStateException;
import com.sporya.match.domain.Match;
import com.sporya.match.domain.MatchAccessDeniedException;
import com.sporya.match.domain.MatchEvent;
import com.sporya.match.domain.MatchEventType;
import com.sporya.match.domain.MatchNotFoundException;
import com.sporya.match.domain.MatchStatus;
import com.sporya.match.domain.PlayerNotInMatchException;
import com.sporya.match.domain.RedCardViolationException;
import com.sporya.match.infrastructure.persistence.MatchEventRepository;
import com.sporya.match.infrastructure.persistence.MatchRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchEventService {

  private final MatchRepository matchRepository;
  private final MatchEventRepository matchEventRepository;
  private final TeamService teamService;
  private final PlayerService playerService;

  public MatchEventService(
      MatchRepository matchRepository,
      MatchEventRepository matchEventRepository,
      TeamService teamService,
      PlayerService playerService) {
    this.matchRepository = matchRepository;
    this.matchEventRepository = matchEventRepository;
    this.teamService = teamService;
    this.playerService = playerService;
  }

  @Transactional
  public MatchEventResponse add(
      AuthenticatedUser caller, UUID matchId, CreateMatchEventRequest request) {
    Match match =
        matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
    if (match.getStatus() != MatchStatus.LIVE) {
      throw new InvalidMatchStateException(
          "Match must be LIVE to add an event, was: " + match.getStatus());
    }
    UUID homeClubId = teamService.get(match.getHomeTeamId()).clubId();
    UUID awayClubId = teamService.get(match.getAwayTeamId()).clubId();
    boolean authorized =
        caller.hasAnyRole(homeClubId, Role.ADMIN, Role.COACH)
            || caller.hasAnyRole(awayClubId, Role.ADMIN, Role.COACH);
    if (!authorized) {
      throw new MatchAccessDeniedException("Not authorized to manage this match");
    }
    UUID playerTeamId = playerService.get(request.playerId()).teamId();
    if (!playerTeamId.equals(match.getHomeTeamId())
        && !playerTeamId.equals(match.getAwayTeamId())) {
      throw new PlayerNotInMatchException(request.playerId(), matchId);
    }
    if (request.type() == MatchEventType.GOAL_SCORED
        && matchEventRepository.existsByMatchIdAndPlayerIdAndType(
            matchId, request.playerId(), MatchEventType.RED_CARD)) {
      throw new RedCardViolationException(request.playerId(), matchId);
    }
    MatchEvent event =
        new MatchEvent(matchId, request.type(), request.minute(), request.playerId(), playerTeamId);
    return MatchEventResponse.from(matchEventRepository.save(event));
  }

  @Transactional(readOnly = true)
  public List<MatchEventResponse> listForMatch(UUID matchId) {
    if (!matchRepository.existsById(matchId)) {
      throw new MatchNotFoundException(matchId);
    }
    return matchEventRepository.findByMatchIdOrderByMinuteAsc(matchId).stream()
        .map(MatchEventResponse::from)
        .toList();
  }
}
