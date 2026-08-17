package com.sporya.statistics.infrastructure.persistence;

import com.sporya.statistics.domain.PlayerMatchStatistics;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerMatchStatisticsRepository
    extends JpaRepository<PlayerMatchStatistics, UUID> {

  List<PlayerMatchStatistics> findByPlayerIdAndSeasonId(UUID playerId, UUID seasonId);
}
