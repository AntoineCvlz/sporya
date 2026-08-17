package com.sporya.match.application;

import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.club.application.TeamService;
import com.sporya.match.controller.dto.CreateMatchRequest;
import com.sporya.match.controller.dto.MatchResponse;
import com.sporya.match.domain.InvalidMatchStateException;
import com.sporya.match.domain.Match;
import com.sporya.match.domain.MatchAccessDeniedException;
import com.sporya.match.domain.MatchEventType;
import com.sporya.match.domain.MatchNotFoundException;
import com.sporya.match.domain.MatchStatus;
import com.sporya.match.domain.SeasonNotFoundException;
import com.sporya.match.infrastructure.persistence.MatchEventRepository;
import com.sporya.match.infrastructure.persistence.MatchRepository;
import com.sporya.match.infrastructure.persistence.SeasonRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {

  private final MatchRepository matchRepository;
  private final SeasonRepository seasonRepository;
  private final MatchEventRepository matchEventRepository;
  private final TeamService teamService;

  public MatchService(
      MatchRepository matchRepository,
      SeasonRepository seasonRepository,
      MatchEventRepository matchEventRepository,
      TeamService teamService) {
    this.matchRepository = matchRepository;
    this.seasonRepository = seasonRepository;
    this.matchEventRepository = matchEventRepository;
    this.teamService = teamService;
  }

  @Transactional
  public MatchResponse create(AuthenticatedUser caller, CreateMatchRequest request) {
    if (!seasonRepository.existsById(request.seasonId())) {
      throw new SeasonNotFoundException(request.seasonId());
    }
    UUID homeClubId = teamService.get(request.homeTeamId()).clubId();
    teamService.get(request.awayTeamId());
    if (!caller.hasAnyRole(homeClubId, Role.ADMIN, Role.COACH)) {
      throw new MatchAccessDeniedException("Not authorized to create a match for this home team");
    }
    Match match =
        new Match(
            request.seasonId(), request.homeTeamId(), request.awayTeamId(), request.kickoffAt());
    return toResponse(matchRepository.save(match));
  }

  @Transactional(readOnly = true)
  public List<MatchResponse> list() {
    return matchRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public MatchResponse get(UUID matchId) {
    return toResponse(findMatch(matchId));
  }

  @Transactional
  public MatchResponse start(AuthenticatedUser caller, UUID matchId) {
    Match match = findMatch(matchId);
    requireHomeOrAwayStaff(caller, match);
    if (match.getStatus() != MatchStatus.SCHEDULED) {
      throw new InvalidMatchStateException(
          "Match must be SCHEDULED to start, was: " + match.getStatus());
    }
    match.setStatus(MatchStatus.LIVE);
    return toResponse(matchRepository.save(match));
  }

  @Transactional
  public MatchResponse halfTime(AuthenticatedUser caller, UUID matchId) {
    Match match = findMatch(matchId);
    requireHomeOrAwayStaff(caller, match);
    if (match.getStatus() != MatchStatus.LIVE) {
      throw new InvalidMatchStateException(
          "Match must be LIVE to go to half-time, was: " + match.getStatus());
    }
    match.setStatus(MatchStatus.HALF_TIME);
    return toResponse(matchRepository.save(match));
  }

  @Transactional
  public MatchResponse resume(AuthenticatedUser caller, UUID matchId) {
    Match match = findMatch(matchId);
    requireHomeOrAwayStaff(caller, match);
    if (match.getStatus() != MatchStatus.HALF_TIME) {
      throw new InvalidMatchStateException(
          "Match must be HALF_TIME to resume, was: " + match.getStatus());
    }
    match.setStatus(MatchStatus.LIVE);
    return toResponse(matchRepository.save(match));
  }

  @Transactional
  public MatchResponse finish(AuthenticatedUser caller, UUID matchId) {
    Match match = findMatch(matchId);
    requireHomeOrAwayStaff(caller, match);
    if (match.getStatus() != MatchStatus.LIVE) {
      throw new InvalidMatchStateException(
          "Match must be LIVE to finish, was: " + match.getStatus());
    }
    match.setStatus(MatchStatus.FINISHED);
    return toResponse(matchRepository.save(match));
  }

  Match findMatch(UUID matchId) {
    return matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
  }

  void requireHomeOrAwayStaff(AuthenticatedUser caller, Match match) {
    UUID homeClubId = teamService.get(match.getHomeTeamId()).clubId();
    UUID awayClubId = teamService.get(match.getAwayTeamId()).clubId();
    boolean authorized =
        caller.hasAnyRole(homeClubId, Role.ADMIN, Role.COACH)
            || caller.hasAnyRole(awayClubId, Role.ADMIN, Role.COACH);
    if (!authorized) {
      throw new MatchAccessDeniedException("Not authorized to manage this match");
    }
  }

  private MatchResponse toResponse(Match match) {
    int homeScore =
        (int)
            matchEventRepository.countByMatchIdAndTeamIdAndType(
                match.getId(), match.getHomeTeamId(), MatchEventType.GOAL_SCORED);
    int awayScore =
        (int)
            matchEventRepository.countByMatchIdAndTeamIdAndType(
                match.getId(), match.getAwayTeamId(), MatchEventType.GOAL_SCORED);
    return MatchResponse.from(match, homeScore, awayScore);
  }
}
