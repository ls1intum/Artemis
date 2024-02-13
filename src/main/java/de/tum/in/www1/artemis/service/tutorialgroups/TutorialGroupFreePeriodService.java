package de.tum.in.www1.artemis.service.tutorialgroups;

import java.util.*;

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

        // Only cancel active sessions
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
     * Update all tutorial group sessions that were cancelled because of the given free period. Sessions that overlap with another free period are not activated.
     *
     * @param tutorialGroupFreePeriod the free period
     * @param onDeletion              True if the free period is deleted, false otherwise.
     */
    public void updateOverlappingSessions(Course course, TutorialGroupFreePeriod tutorialGroupFreePeriod, boolean onDeletion) {
        Set<TutorialGroupSession> overlappingSessions = tutorialGroupSessionRepository.findAllBetween(course, tutorialGroupFreePeriod.getStart(), tutorialGroupFreePeriod.getEnd());

        findAndUpdateStillCanceledSessions(course, tutorialGroupFreePeriod, overlappingSessions, onDeletion);
        findAndUpdateSessionsToReactivate(course, tutorialGroupFreePeriod, overlappingSessions);
    }

    /**
     * Find and update tutorial group sessions that are still cancelled due to overlapping with another free period.
     * If the Period that provided the reason initially is still present, the reason remains the same.
     * If it is not, it finds the first overlapping free period and updates the session status, status explanation, and the tutorial group free period.
     *
     * @param course                  The course in which the free period is defined.
     * @param tutorialGroupFreePeriod The free period.
     * @param overlappingSessions     The set of sessions that overlap with the given free period.
     * @param onDeletion              True if the free period is deleted, false otherwise.
     */
    private void findAndUpdateStillCanceledSessions(Course course, TutorialGroupFreePeriod tutorialGroupFreePeriod, Set<TutorialGroupSession> overlappingSessions,
            boolean onDeletion) {
        // Find those sessions that are still overlapping with another free period
        List<TutorialGroupSession> sessionsStillOverlappingWithFreePeriods = overlappingSessions.stream().filter(session -> {
            Set<TutorialGroupFreePeriod> overlappingPeriods = tutorialGroupFreePeriodRepository
                    .findOverlappingInSameCourse(tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getCourse(), session.getStart(), session.getEnd());
            return overlappingPeriods.size() > 1;
        }).toList();

        sessionsStillOverlappingWithFreePeriods.forEach(session -> {
            // if the FreePeriod should get deleted and the session was cancelled because of the free period, we update the TutorialFreePeriod to another free period
            if (onDeletion && session.getTutorialGroupFreePeriod().equals(tutorialGroupFreePeriod)) {
                TutorialGroupFreePeriod replacementFreePeriod = tutorialGroupFreePeriodRepository.findOverlappingInSameCourse(course, session.getStart(), session.getEnd()).stream()
                        .filter(period -> !period.equals(tutorialGroupFreePeriod)).findFirst().orElseThrow(() -> new IllegalStateException("No replacement FreePeriod found"));
                session.setStatus(TutorialGroupSessionStatus.CANCELLED);
                session.setStatusExplanation(null);
                session.setTutorialGroupFreePeriod(replacementFreePeriod);
                return;
            }

            // if one of the still overlapping FreePeriods is the same as the one that caused the session to be cancelled in the first place, we don't update the status and the
            // explanation
            if (tutorialGroupFreePeriodRepository.findOverlappingInSameCourse(course, session.getStart(), session.getEnd()).contains(tutorialGroupFreePeriod)) {
                return;
            }

            Optional<TutorialGroupFreePeriod> freePeriod = tutorialGroupFreePeriodRepository.findOverlappingInSameCourse(course, session.getStart(), session.getEnd()).stream()
                    .findFirst();
            // If the session was cancelled because of the free period, we update the status and the explanation
            if (session.getTutorialGroupFreePeriod().equals(tutorialGroupFreePeriod) && !onDeletion && freePeriod.isPresent()) {
                session.setStatus(TutorialGroupSessionStatus.CANCELLED);
                session.setStatusExplanation(null);
                session.setTutorialGroupFreePeriod(freePeriod.get());
            }
        });
        tutorialGroupSessionRepository.saveAll(sessionsStillOverlappingWithFreePeriods);
    }

    /**
     * Find and reactivate tutorial group sessions that were cancelled due to overlapping with a free period that is mow removed.
     *
     * @param course                  The course in which the free period is defined.
     * @param tutorialGroupFreePeriod The free period.
     * @param overlappingSessions     The set of sessions that overlap with the given free period.
     */
    private void findAndUpdateSessionsToReactivate(Course course, TutorialGroupFreePeriod tutorialGroupFreePeriod, Set<TutorialGroupSession> overlappingSessions) {
        // Filter out those sessions that are still overlapping with another free period
        List<TutorialGroupSession> sessionsToReactivate = overlappingSessions.stream().filter(session -> {
            Set<TutorialGroupFreePeriod> overlappingPeriods = tutorialGroupFreePeriodRepository
                    .findOverlappingInSameCourse(tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getCourse(), session.getStart(), session.getEnd());
            return overlappingPeriods.size() <= 1;
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
