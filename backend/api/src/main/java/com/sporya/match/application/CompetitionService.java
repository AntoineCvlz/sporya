package com.sporya.match.application;

import com.sporya.match.controller.dto.CompetitionResponse;
import com.sporya.match.controller.dto.CreateCompetitionRequest;
import com.sporya.match.domain.Competition;
import com.sporya.match.domain.CompetitionNotFoundException;
import com.sporya.match.infrastructure.persistence.CompetitionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompetitionService {

  private final CompetitionRepository competitionRepository;

  public CompetitionService(CompetitionRepository competitionRepository) {
    this.competitionRepository = competitionRepository;
  }

  @Transactional
  public CompetitionResponse create(CreateCompetitionRequest request) {
    Competition competition = new Competition(request.name());
    return CompetitionResponse.from(competitionRepository.save(competition));
  }

  @Transactional(readOnly = true)
  public List<CompetitionResponse> list() {
    return competitionRepository.findAll().stream().map(CompetitionResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public CompetitionResponse get(UUID competitionId) {
    Competition competition =
        competitionRepository
            .findById(competitionId)
            .orElseThrow(() -> new CompetitionNotFoundException(competitionId));
    return CompetitionResponse.from(competition);
  }
}
