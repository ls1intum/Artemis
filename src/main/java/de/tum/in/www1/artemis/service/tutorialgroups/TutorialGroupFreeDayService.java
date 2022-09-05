package de.tum.in.www1.artemis.service.tutorialgroups;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreeDay;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupSessionRepository;

@Service
public class TutorialGroupFreeDayService {

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    public TutorialGroupFreeDayService(TutorialGroupSessionRepository tutorialGroupSessionRepository) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
    }

    public void cancelActiveOverlappingSessions(TutorialGroupFreeDay tutorialGroupFreeDay) {
        var startAndEnd = getStartAndEndOfFreeDay(tutorialGroupFreeDay);
        var overlappingSessions = tutorialGroupSessionRepository.findAllActiveBetween(startAndEnd.getFirst(), startAndEnd.getSecond());
        overlappingSessions.forEach(session -> {
            session.setStatus(TutorialGroupSessionStatus.CANCELLED);
            if (tutorialGroupFreeDay.getReason() != null) {
                session.setStatusExplanation(tutorialGroupFreeDay.getReason());
            }
        });
        tutorialGroupSessionRepository.saveAll(overlappingSessions);
    }

    public void activateCancelledOverlappingSessions(TutorialGroupFreeDay tutorialGroupFreeDay) {
        var startAndEnd = getStartAndEndOfFreeDay(tutorialGroupFreeDay);
        var overlappingSessions = tutorialGroupSessionRepository.findAllCancelledBetween(startAndEnd.getFirst(), startAndEnd.getSecond());
        overlappingSessions.forEach(session -> {
            session.setStatus(TutorialGroupSessionStatus.ACTIVE);
            session.setStatusExplanation(null);
        });
        tutorialGroupSessionRepository.saveAll(overlappingSessions);
    }

    public Pair<ZonedDateTime, ZonedDateTime> getStartAndEndOfFreeDay(TutorialGroupFreeDay tutorialGroupFreeDay) {
        var date = tutorialGroupFreeDay.getDate();
        var timeZone = ZoneId.of(tutorialGroupFreeDay.getTutorialGroupsConfiguration().getTimeZone());
        var start = ZonedDateTime.of(date, LocalTime.MIN, timeZone);
        var end = ZonedDateTime.of(date, LocalTime.MAX, timeZone);
        return Pair.of(start, end);
    }

}
