package com.sporya.statistics.infrastructure.persistence;

import com.sporya.statistics.domain.TeamMatchStatistics;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMatchStatisticsRepository extends JpaRepository<TeamMatchStatistics, UUID> {

  List<TeamMatchStatistics> findByTeamIdAndSeasonId(UUID teamId, UUID seasonId);
}
