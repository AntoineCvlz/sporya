package com.sporya.match.infrastructure.persistence;

import com.sporya.match.domain.Match;
import com.sporya.match.domain.MatchStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MatchRepository extends JpaRepository<Match, UUID> {

  @Query(
      "SELECT m FROM Match m WHERE (m.homeTeamId = :teamId OR m.awayTeamId = :teamId) "
          + "AND m.status = :status ORDER BY m.kickoffAt DESC")
  List<Match> findRecentByTeamAndStatus(UUID teamId, MatchStatus status, Pageable pageable);
}
