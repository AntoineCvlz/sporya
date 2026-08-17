package com.sporya.match.controller;

import com.sporya.match.application.SeasonService;
import com.sporya.match.controller.dto.CreateSeasonRequest;
import com.sporya.match.controller.dto.SeasonResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeasonController {

  private final SeasonService seasonService;

  public SeasonController(SeasonService seasonService) {
    this.seasonService = seasonService;
  }

  @PostMapping("/api/v1/competitions/{competitionId}/seasons")
  @ResponseStatus(HttpStatus.CREATED)
  public SeasonResponse create(
      @PathVariable UUID competitionId, @Valid @RequestBody CreateSeasonRequest request) {
    return seasonService.create(competitionId, request);
  }

  @GetMapping("/api/v1/competitions/{competitionId}/seasons")
  public List<SeasonResponse> listByCompetition(@PathVariable UUID competitionId) {
    return seasonService.listByCompetition(competitionId);
  }

  @GetMapping("/api/v1/seasons/{seasonId}")
  public SeasonResponse get(@PathVariable UUID seasonId) {
    return seasonService.get(seasonId);
  }
}
