package com.sporya.club.application;

import com.sporya.auth.application.AuthenticationService;
import com.sporya.auth.application.MembershipService;
import com.sporya.auth.domain.ClubMembership;
import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import com.sporya.club.controller.dto.AddMemberRequest;
import com.sporya.club.controller.dto.MemberResponse;
import com.sporya.club.domain.ClubAccessDeniedException;
import com.sporya.club.domain.ClubNotFoundException;
import com.sporya.club.infrastructure.persistence.ClubRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClubMemberService {

  private final ClubRepository clubRepository;
  private final MembershipService membershipService;
  private final AuthenticationService authenticationService;

  public ClubMemberService(
      ClubRepository clubRepository,
      MembershipService membershipService,
      AuthenticationService authenticationService) {
    this.clubRepository = clubRepository;
    this.membershipService = membershipService;
    this.authenticationService = authenticationService;
  }

  @Transactional
  public MemberResponse add(AuthenticatedUser caller, UUID clubId, AddMemberRequest request) {
    if (!clubRepository.existsById(clubId)) {
      throw new ClubNotFoundException(clubId);
    }
    if (!caller.hasAnyRole(clubId, Role.ADMIN)) {
      throw new ClubAccessDeniedException(clubId);
    }
    UUID targetUserId = authenticationService.findUserIdByEmail(request.email());
    ClubMembership membership = membershipService.grant(targetUserId, clubId, request.role());
    return new MemberResponse(
        targetUserId, request.email(), request.role(), membership.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public List<MemberResponse> list(UUID clubId) {
    if (!clubRepository.existsById(clubId)) {
      throw new ClubNotFoundException(clubId);
    }
    return membershipService.listForClub(clubId).stream()
        .map(
            membership ->
                new MemberResponse(
                    membership.getUserId(),
                    authenticationService.currentUser(membership.getUserId()).email(),
                    membership.getRole(),
                    membership.getCreatedAt()))
        .toList();
  }
}
