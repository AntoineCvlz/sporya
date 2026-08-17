package com.sporya.auth.infrastructure.security;

import com.sporya.auth.domain.ClubRole;
import com.sporya.auth.domain.Role;
import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, List<ClubRole> memberships) {

  public boolean hasAnyRole(UUID clubId, Role... roles) {
    List<Role> wanted = List.of(roles);
    return memberships.stream()
        .anyMatch(
            membership -> membership.clubId().equals(clubId) && wanted.contains(membership.role()));
  }
}
