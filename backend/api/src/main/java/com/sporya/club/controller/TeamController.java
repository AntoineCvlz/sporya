package com.sporya.club.controller;

import com.sporya.club.application.TeamService;
import com.sporya.club.controller.dto.CreateTeamRequest;
import com.sporya.club.controller.dto.TeamResponse;
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
public class TeamController {

  private final TeamService teamService;

  public TeamController(TeamService teamService) {
    this.teamService = teamService;
  }

  @PostMapping("/api/v1/clubs/{clubId}/teams")
  @ResponseStatus(HttpStatus.CREATED)
  public TeamResponse create(
      @PathVariable UUID clubId, @Valid @RequestBody CreateTeamRequest request) {
    return teamService.create(clubId, request);
  }

  @GetMapping("/api/v1/clubs/{clubId}/teams")
  public List<TeamResponse> listByClub(@PathVariable UUID clubId) {
    return teamService.listByClub(clubId);
  }

  @GetMapping("/api/v1/teams/{teamId}")
  public TeamResponse get(@PathVariable UUID teamId) {
    return teamService.get(teamId);
  }
}
