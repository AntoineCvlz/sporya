package com.sporya.match.infrastructure.persistence;

import com.sporya.match.domain.Season;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonRepository extends JpaRepository<Season, UUID> {

  List<Season> findByCompetitionId(UUID competitionId);
}
