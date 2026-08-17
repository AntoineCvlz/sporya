package com.sporya.auth.application;

import com.sporya.auth.domain.ClubMembership;
import com.sporya.auth.domain.ClubRole;
import com.sporya.auth.domain.Role;
import com.sporya.auth.infrastructure.persistence.ClubMembershipRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

  private final ClubMembershipRepository clubMembershipRepository;

  public MembershipService(ClubMembershipRepository clubMembershipRepository) {
    this.clubMembershipRepository = clubMembershipRepository;
  }

  @Transactional
  public ClubMembership grant(UUID userId, UUID clubId, Role role) {
    return clubMembershipRepository.save(new ClubMembership(userId, clubId, role));
  }

  @Transactional(readOnly = true)
  public List<ClubRole> membershipsFor(UUID userId) {
    return clubMembershipRepository.findByUserId(userId).stream()
        .map(membership -> new ClubRole(membership.getClubId(), membership.getRole()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ClubMembership> listForClub(UUID clubId) {
    return clubMembershipRepository.findByClubId(clubId);
  }
}
