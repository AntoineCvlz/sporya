package com.sporya.club.infrastructure.persistence;

import com.sporya.club.domain.Team;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {

  List<Team> findByClubId(UUID clubId);
}
