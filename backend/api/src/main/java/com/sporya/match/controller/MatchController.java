package com.sporya.match.controller;

import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.match.application.MatchService;
import com.sporya.match.controller.dto.CreateMatchRequest;
import com.sporya.match.controller.dto.MatchResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

  private final MatchService matchService;

  public MatchController(MatchService matchService) {
    this.matchService = matchService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MatchResponse create(
      @AuthenticationPrincipal AuthenticatedUser caller,
      @Valid @RequestBody CreateMatchRequest request) {
    return matchService.create(caller, request);
  }

  @GetMapping
  public List<MatchResponse> list() {
    return matchService.list();
  }

  @GetMapping("/{matchId}")
  public MatchResponse get(@PathVariable UUID matchId) {
    return matchService.get(matchId);
  }

  @PostMapping("/{matchId}/start")
  public MatchResponse start(
      @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID matchId) {
    return matchService.start(caller, matchId);
  }

  @PostMapping("/{matchId}/half-time")
  public MatchResponse halfTime(
      @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID matchId) {
    return matchService.halfTime(caller, matchId);
  }

  @PostMapping("/{matchId}/resume")
  public MatchResponse resume(
      @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID matchId) {
    return matchService.resume(caller, matchId);
  }

  @PostMapping("/{matchId}/finish")
  public MatchResponse finish(
      @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID matchId) {
    return matchService.finish(caller, matchId);
  }
}
