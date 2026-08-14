package com.sporya.club.application;

import com.sporya.club.controller.dto.CreateTeamRequest;
import com.sporya.club.controller.dto.TeamResponse;
import com.sporya.club.domain.ClubNotFoundException;
import com.sporya.club.domain.Team;
import com.sporya.club.domain.TeamNotFoundException;
import com.sporya.club.infrastructure.persistence.ClubRepository;
import com.sporya.club.infrastructure.persistence.TeamRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

  private final TeamRepository teamRepository;
  private final ClubRepository clubRepository;

  public TeamService(TeamRepository teamRepository, ClubRepository clubRepository) {
    this.teamRepository = teamRepository;
    this.clubRepository = clubRepository;
  }

  @Transactional
  public TeamResponse create(UUID clubId, CreateTeamRequest request) {
    if (!clubRepository.existsById(clubId)) {
      throw new ClubNotFoundException(clubId);
    }
    Team team = new Team(request.name(), clubId);
    return TeamResponse.from(teamRepository.save(team));
  }

  @Transactional(readOnly = true)
  public List<TeamResponse> listByClub(UUID clubId) {
    if (!clubRepository.existsById(clubId)) {
      throw new ClubNotFoundException(clubId);
    }
    return teamRepository.findByClubId(clubId).stream().map(TeamResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public TeamResponse get(UUID teamId) {
    Team team =
        teamRepository.findById(teamId).orElseThrow(() -> new TeamNotFoundException(teamId));
    return TeamResponse.from(team);
  }
}
