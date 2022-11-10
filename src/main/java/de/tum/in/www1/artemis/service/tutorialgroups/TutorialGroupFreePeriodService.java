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
     * Cancel all tutorial group sessions that overlap with the given free period
     *
     * @param course                  the course in which the free period is defined
     * @param tutorialGroupFreePeriod the free period
     */
    public void cancelOverlappingSessions(Course course, TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        var overlappingSessions = tutorialGroupSessionRepository.findAllBetween(course, tutorialGroupFreePeriod.getStart(), tutorialGroupFreePeriod.getEnd());
        overlappingSessions.forEach(session -> {
            session.setStatus(TutorialGroupSessionStatus.CANCELLED);
            // we set the status explanation to null, because the reason is now contained in the free period
            session.setStatusExplanation(null);
            session.setTutorialGroupFreePeriod(tutorialGroupFreePeriod);
        });
        tutorialGroupSessionRepository.saveAll(overlappingSessions);
    }

    /**
     * Activate all tutorial group sessions that were cancelled because of the given free period
     *
     * @param tutorialGroupFreePeriod the free period
     */
    public void activateOverlappingSessions(TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        var overlappingSessions = tutorialGroupSessionRepository.findAllByTutorialGroupFreePeriodId(tutorialGroupFreePeriod.getId());
        overlappingSessions.forEach(session -> {
            session.setStatus(TutorialGroupSessionStatus.ACTIVE);
            session.setStatusExplanation(null);
            session.setTutorialGroupFreePeriod(null);
        });
        tutorialGroupSessionRepository.saveAll(overlappingSessions);
    }

}
