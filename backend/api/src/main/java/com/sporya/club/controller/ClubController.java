package com.sporya.club.controller;

import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.club.application.ClubService;
import com.sporya.club.controller.dto.ClubResponse;
import com.sporya.club.controller.dto.CreateClubRequest;
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
@RequestMapping("/api/v1/clubs")
public class ClubController {

  private final ClubService clubService;

  public ClubController(ClubService clubService) {
    this.clubService = clubService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ClubResponse create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @Valid @RequestBody CreateClubRequest request) {
    return clubService.create(user.userId(), request);
  }

  @GetMapping
  public List<ClubResponse> list() {
    return clubService.list();
  }

  @GetMapping("/{clubId}")
  public ClubResponse get(@PathVariable UUID clubId) {
    return clubService.get(clubId);
  }
}
