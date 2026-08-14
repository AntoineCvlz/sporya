package com.sporya.club.infrastructure.persistence;

import com.sporya.club.domain.Player;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

  List<Player> findByTeamId(UUID teamId);
}
