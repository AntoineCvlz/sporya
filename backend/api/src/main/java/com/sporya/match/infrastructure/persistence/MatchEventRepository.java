package com.sporya.match.infrastructure.persistence;

import com.sporya.match.domain.MatchEvent;
import com.sporya.match.domain.MatchEventType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchEventRepository extends JpaRepository<MatchEvent, UUID> {

  List<MatchEvent> findByMatchIdOrderByMinuteAsc(UUID matchId);

  boolean existsByMatchIdAndPlayerIdAndType(UUID matchId, UUID playerId, MatchEventType type);

  long countByMatchIdAndTeamIdAndType(UUID matchId, UUID teamId, MatchEventType type);
}
