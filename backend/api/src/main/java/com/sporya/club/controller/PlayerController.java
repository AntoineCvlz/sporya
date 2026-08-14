package com.sporya.club.controller;

import com.sporya.club.application.PlayerService;
import com.sporya.club.controller.dto.CreatePlayerRequest;
import com.sporya.club.controller.dto.PlayerResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teams/{teamId}/players")
public class PlayerController {

  private final PlayerService playerService;

  public PlayerController(PlayerService playerService) {
    this.playerService = playerService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PlayerResponse create(
      @PathVariable UUID teamId, @Valid @RequestBody CreatePlayerRequest request) {
    return playerService.create(teamId, request);
  }

  @GetMapping
  public List<PlayerResponse> list(@PathVariable UUID teamId) {
    return playerService.listByTeam(teamId);
  }
}
