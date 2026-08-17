package com.sporya.match.controller;

import com.sporya.match.application.MatchService;
import com.sporya.match.controller.dto.TeamFormResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamFormController {

  private final MatchService matchService;

  public TeamFormController(MatchService matchService) {
    this.matchService = matchService;
  }

  @GetMapping("/{teamId}/form")
  public TeamFormResponse form(@PathVariable UUID teamId) {
    return matchService.recentForm(teamId);
  }
}
