package com.sporya.club.application;

import com.sporya.club.controller.dto.CreatePlayerRequest;
import com.sporya.club.controller.dto.PlayerResponse;
import com.sporya.club.domain.Player;
import com.sporya.club.domain.TeamNotFoundException;
import com.sporya.club.infrastructure.persistence.PlayerRepository;
import com.sporya.club.infrastructure.persistence.TeamRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

  private final PlayerRepository playerRepository;
  private final TeamRepository teamRepository;

  public PlayerService(PlayerRepository playerRepository, TeamRepository teamRepository) {
    this.playerRepository = playerRepository;
    this.teamRepository = teamRepository;
  }

  @Transactional
  public PlayerResponse create(UUID teamId, CreatePlayerRequest request) {
    if (!teamRepository.existsById(teamId)) {
      throw new TeamNotFoundException(teamId);
    }
    Player player = new Player(request.name(), request.birthdate(), request.position(), teamId);
    return PlayerResponse.from(playerRepository.save(player));
  }

  @Transactional(readOnly = true)
  public List<PlayerResponse> listByTeam(UUID teamId) {
    if (!teamRepository.existsById(teamId)) {
      throw new TeamNotFoundException(teamId);
    }
    return playerRepository.findByTeamId(teamId).stream().map(PlayerResponse::from).toList();
  }
}
