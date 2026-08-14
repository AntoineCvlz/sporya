package com.sporya.club.application;

import com.sporya.club.controller.dto.ClubResponse;
import com.sporya.club.controller.dto.CreateClubRequest;
import com.sporya.club.domain.Club;
import com.sporya.club.domain.ClubNotFoundException;
import com.sporya.club.infrastructure.persistence.ClubRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClubService {

  private final ClubRepository clubRepository;

  public ClubService(ClubRepository clubRepository) {
    this.clubRepository = clubRepository;
  }

  @Transactional
  public ClubResponse create(UUID createdBy, CreateClubRequest request) {
    Club club = new Club(request.name(), request.country(), createdBy);
    return ClubResponse.from(clubRepository.save(club));
  }

  @Transactional(readOnly = true)
  public List<ClubResponse> list() {
    return clubRepository.findAll().stream().map(ClubResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public ClubResponse get(UUID clubId) {
    Club club =
        clubRepository.findById(clubId).orElseThrow(() -> new ClubNotFoundException(clubId));
    return ClubResponse.from(club);
  }
}
