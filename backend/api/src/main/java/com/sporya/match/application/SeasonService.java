package com.sporya.match.application;

import com.sporya.match.controller.dto.CreateSeasonRequest;
import com.sporya.match.controller.dto.SeasonResponse;
import com.sporya.match.domain.CompetitionNotFoundException;
import com.sporya.match.domain.Season;
import com.sporya.match.domain.SeasonNotFoundException;
import com.sporya.match.infrastructure.persistence.CompetitionRepository;
import com.sporya.match.infrastructure.persistence.SeasonRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeasonService {

  private final SeasonRepository seasonRepository;
  private final CompetitionRepository competitionRepository;

  public SeasonService(
      SeasonRepository seasonRepository, CompetitionRepository competitionRepository) {
    this.seasonRepository = seasonRepository;
    this.competitionRepository = competitionRepository;
  }

  @Transactional
  public SeasonResponse create(UUID competitionId, CreateSeasonRequest request) {
    if (!competitionRepository.existsById(competitionId)) {
      throw new CompetitionNotFoundException(competitionId);
    }
    Season season = new Season(request.label(), competitionId);
    return SeasonResponse.from(seasonRepository.save(season));
  }

  @Transactional(readOnly = true)
  public List<SeasonResponse> listByCompetition(UUID competitionId) {
    if (!competitionRepository.existsById(competitionId)) {
      throw new CompetitionNotFoundException(competitionId);
    }
    return seasonRepository.findByCompetitionId(competitionId).stream()
        .map(SeasonResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public SeasonResponse get(UUID seasonId) {
    Season season =
        seasonRepository
            .findById(seasonId)
            .orElseThrow(() -> new SeasonNotFoundException(seasonId));
    return SeasonResponse.from(season);
  }
}
