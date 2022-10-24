package de.tum.in.www1.artemis.service.tutorialgroups;

import java.util.Optional;

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

    public Optional<TutorialGroupFreePeriod> findOverlappingPeriod(Course course, TutorialGroupSession tutorialGroupSession) {
        return tutorialGroupFreePeriodRepository.findOverlappingInSameCourse(course, tutorialGroupSession.getStart(), tutorialGroupSession.getEnd());
    }

    /**
     * Cancel all active tutorial group sessions that overlap with the given free period
     *
     * @param course                  the course in which the free period is defined
     * @param tutorialGroupFreePeriod the free period
     */
    public void cancelActiveOverlappingSessions(Course course, TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        var overlappingSessions = tutorialGroupSessionRepository.findAllActiveBetween(course, tutorialGroupFreePeriod.getStart(), tutorialGroupFreePeriod.getEnd());
        overlappingSessions.forEach(session -> {
            session.setStatus(TutorialGroupSessionStatus.CANCELLED);
            if (tutorialGroupFreePeriod.getReason() != null) {
                session.setStatusExplanation(tutorialGroupFreePeriod.getReason());
            }
        });
        tutorialGroupSessionRepository.saveAll(overlappingSessions);
    }

    /**
     * Activate all cancelled tutorial group sessions that overlap with the given free period
     *
     * @param course                  the course in which the free period is defined
     * @param tutorialGroupFreePeriod the free period
     */
    public void activateCancelledOverlappingSessions(Course course, TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        var overlappingSessions = tutorialGroupSessionRepository.findAllCancelledBetween(course, tutorialGroupFreePeriod.getStart(), tutorialGroupFreePeriod.getEnd());
        overlappingSessions.forEach(session -> {
            session.setStatus(TutorialGroupSessionStatus.ACTIVE);
            session.setStatusExplanation(null);
        });
        tutorialGroupSessionRepository.saveAll(overlappingSessions);
    }

}
