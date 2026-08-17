package com.sporya.auth.infrastructure.persistence;

import com.sporya.auth.domain.ClubMembership;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubMembershipRepository extends JpaRepository<ClubMembership, UUID> {

  List<ClubMembership> findByUserId(UUID userId);

  List<ClubMembership> findByClubId(UUID clubId);
}
