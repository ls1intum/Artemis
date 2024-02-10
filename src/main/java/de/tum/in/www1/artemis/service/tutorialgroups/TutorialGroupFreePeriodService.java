package de.tum.in.www1.artemis.service.tutorialgroups;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreePeriod;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupFreePeriodRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupSessionRepository;

@Service
public class TutorialGroupFreePeriodService {

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    public TutorialGroupFreePeriodService(TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupFreePeriodRepository = tutorialGroupFreePeriodRepository;
    }

    public Optional<TutorialGroupFreePeriod> findOverlappingPeriods(Course course, TutorialGroupSession tutorialGroupSession) {
        return tutorialGroupFreePeriodRepository.findOverlappingInSameCourse(course, tutorialGroupSession.getStart(), tutorialGroupSession.getEnd()).stream().findFirst();
    }

    /**
     * Cancel all tutorial group sessions that overlap with the given free period
     *
     * @param course                  the course in which the free period is defined
     * @param tutorialGroupFreePeriod the free period
     */
    public void cancelOverlappingSessions(Course course, TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        var overlappingSessions = tutorialGroupSessionRepository.findAllBetween(course, tutorialGroupFreePeriod.getStart(), tutorialGroupFreePeriod.getEnd());
        List<TutorialGroupSession> sessionsToCancel = getActiveSessionsFromSet(overlappingSessions);

        sessionsToCancel.forEach(session -> {
            session.setStatus(TutorialGroupSessionStatus.CANCELLED);
            // we set the status explanation to null, because the reason is now contained in the free period
            session.setStatusExplanation(null);
            session.setTutorialGroupFreePeriod(tutorialGroupFreePeriod);
        });
        tutorialGroupSessionRepository.saveAll(sessionsToCancel);
    }

    /**
     * Activate all tutorial group sessions that were cancelled because of the given free period. Sessions that overlap with another free period are not activated.
     *
     * @param tutorialGroupFreePeriod the free period
     */
    public void activateOverlappingSessions(TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        var overlappingSessions = tutorialGroupSessionRepository.findAllByTutorialGroupFreePeriodId(tutorialGroupFreePeriod.getId());

        // Filter out those sessions that are still overlapping with another free period
        List<TutorialGroupSession> sessionsToReactivate = overlappingSessions.stream().filter(session -> {
            Set<TutorialGroupFreePeriod> overlappingPeriods = tutorialGroupFreePeriodRepository
                    .findOverlappingInSameCourse(tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getCourse(), session.getStart(), session.getEnd());
            System.out.println("Overlapping periods: " + overlappingPeriods.size());
            return overlappingPeriods.size() < 1;
        }).toList();

        sessionsToReactivate.forEach(session -> {
            session.setStatus(TutorialGroupSessionStatus.ACTIVE);
            session.setStatusExplanation(null);
            session.setTutorialGroupFreePeriod(null);
        });
        tutorialGroupSessionRepository.saveAll(sessionsToReactivate);
    }

    public List<TutorialGroupSession> getActiveSessionsFromSet(Set<TutorialGroupSession> sessions) {
        return sessions.stream().filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE).toList();
    }

}
