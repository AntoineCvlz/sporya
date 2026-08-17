package com.sporya.match.infrastructure.persistence;

import com.sporya.match.domain.Match;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, UUID> {}
