package com.sporya.match.infrastructure.persistence;

import com.sporya.match.domain.Competition;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<Competition, UUID> {}
