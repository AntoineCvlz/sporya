package com.sporya.notification.application;

import com.sporya.auth.application.MembershipService;
import com.sporya.club.application.TeamService;
import com.sporya.match.application.MatchEventService;
import com.sporya.match.controller.dto.MatchEventResponse;
import com.sporya.match.domain.MatchEventType;
import com.sporya.match.domain.MatchFinishedEvent;
import com.sporya.notification.domain.Notification;
import com.sporya.notification.domain.NotificationType;
import com.sporya.notification.infrastructure.persistence.NotificationRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MatchFinishedNotifier {

  private final TeamService teamService;
  private final MembershipService membershipService;
  private final MatchEventService matchEventService;
  private final NotificationRepository notificationRepository;

  public MatchFinishedNotifier(
      TeamService teamService,
      MembershipService membershipService,
      MatchEventService matchEventService,
      NotificationRepository notificationRepository) {
    this.teamService = teamService;
    this.membershipService = membershipService;
    this.matchEventService = matchEventService;
    this.notificationRepository = notificationRepository;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onMatchFinished(MatchFinishedEvent event) {
    UUID homeClubId = teamService.get(event.homeTeamId()).clubId();
    UUID awayClubId = teamService.get(event.awayTeamId()).clubId();

    Set<UUID> recipientIds = new HashSet<>();
    membershipService.listForClub(homeClubId).forEach(m -> recipientIds.add(m.getUserId()));
    membershipService.listForClub(awayClubId).forEach(m -> recipientIds.add(m.getUserId()));

    List<MatchEventResponse> events = matchEventService.listForMatch(event.matchId());
    int homeScore = countGoalsForTeam(events, event.homeTeamId());
    int awayScore = countGoalsForTeam(events, event.awayTeamId());

    for (UUID userId : recipientIds) {
      notificationRepository.save(
          new Notification(
              userId,
              NotificationType.MATCH_FINISHED,
              event.matchId(),
              event.homeTeamId(),
              event.awayTeamId(),
              homeScore,
              awayScore));
    }
  }

  private static int countGoalsForTeam(List<MatchEventResponse> events, UUID teamId) {
    return (int)
        events.stream()
            .filter(e -> e.type() == MatchEventType.GOAL_SCORED && e.teamId().equals(teamId))
            .count();
  }
}
