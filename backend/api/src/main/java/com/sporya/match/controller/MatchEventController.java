package com.sporya.match.controller;

import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.match.application.MatchEventService;
import com.sporya.match.controller.dto.CreateMatchEventRequest;
import com.sporya.match.controller.dto.MatchEventResponse;
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
@RequestMapping("/api/v1/matches/{matchId}/events")
public class MatchEventController {

  private final MatchEventService matchEventService;

  public MatchEventController(MatchEventService matchEventService) {
    this.matchEventService = matchEventService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MatchEventResponse add(
      @AuthenticationPrincipal AuthenticatedUser caller,
      @PathVariable UUID matchId,
      @Valid @RequestBody CreateMatchEventRequest request) {
    return matchEventService.add(caller, matchId, request);
  }

  @GetMapping
  public List<MatchEventResponse> list(@PathVariable UUID matchId) {
    return matchEventService.listForMatch(matchId);
  }
}
