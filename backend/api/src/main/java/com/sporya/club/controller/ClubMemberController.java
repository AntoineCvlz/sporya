package com.sporya.club.controller;

import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.club.application.ClubMemberService;
import com.sporya.club.controller.dto.AddMemberRequest;
import com.sporya.club.controller.dto.MemberResponse;
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
@RequestMapping("/api/v1/clubs/{clubId}/members")
public class ClubMemberController {

  private final ClubMemberService clubMemberService;

  public ClubMemberController(ClubMemberService clubMemberService) {
    this.clubMemberService = clubMemberService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MemberResponse add(
      @AuthenticationPrincipal AuthenticatedUser caller,
      @PathVariable UUID clubId,
      @Valid @RequestBody AddMemberRequest request) {
    return clubMemberService.add(caller, clubId, request);
  }

  @GetMapping
  public List<MemberResponse> list(@PathVariable UUID clubId) {
    return clubMemberService.list(clubId);
  }
}
