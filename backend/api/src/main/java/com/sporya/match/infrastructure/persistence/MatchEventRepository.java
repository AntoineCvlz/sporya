package com.sporya.match.infrastructure.persistence;

import com.sporya.match.domain.MatchEvent;
import com.sporya.match.domain.MatchEventType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MatchEventRepository extends JpaRepository<MatchEvent, UUID> {

  List<MatchEvent> findByMatchIdOrderByMinuteAsc(UUID matchId);

  boolean existsByMatchIdAndPlayerIdAndType(UUID matchId, UUID playerId, MatchEventType type);

  long countByMatchIdAndTeamIdAndType(UUID matchId, UUID teamId, MatchEventType type);

  long countByPlayerIdAndType(UUID playerId, MatchEventType type);

  @Query("SELECT COUNT(DISTINCT e.matchId) FROM MatchEvent e WHERE e.playerId = :playerId")
  long countDistinctMatchesForPlayer(UUID playerId);
}
