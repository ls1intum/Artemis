package de.tum.in.www1.artemis.service.tutorialgroups;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreeDay;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupFreeDayRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupSessionRepository;

@Service
public class TutorialGroupFreeDayService {

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupFreeDayRepository tutorialGroupFreeDayRepository;

    public TutorialGroupFreeDayService(TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupFreeDayRepository tutorialGroupFreeDayRepository) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupFreeDayRepository = tutorialGroupFreeDayRepository;
    }

    public Set<TutorialGroupFreeDay> findOverlappingFreeDays(Course course, TutorialGroupSession tutorialGroupSession) {
        return tutorialGroupFreeDayRepository.onDate(course, tutorialGroupSession.getStart().toLocalDate());
    }

    public void cancelActiveOverlappingSessions(Course course, TutorialGroupFreeDay tutorialGroupFreeDay) {
        var startAndEnd = getStartAndEndOfFreeDay(tutorialGroupFreeDay);
        var overlappingSessions = tutorialGroupSessionRepository.findAllActiveBetween(course, startAndEnd.getFirst(), startAndEnd.getSecond());
        overlappingSessions.forEach(session -> {
            session.setStatus(TutorialGroupSessionStatus.CANCELLED);
            if (tutorialGroupFreeDay.getReason() != null) {
                session.setStatusExplanation(tutorialGroupFreeDay.getReason());
            }
        });
        tutorialGroupSessionRepository.saveAll(overlappingSessions);
    }

    public void activateCancelledOverlappingSessions(Course course, TutorialGroupFreeDay tutorialGroupFreeDay) {
        var startAndEnd = getStartAndEndOfFreeDay(tutorialGroupFreeDay);
        var overlappingSessions = tutorialGroupSessionRepository.findAllCancelledBetween(course, startAndEnd.getFirst(), startAndEnd.getSecond());
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
