package de.tum.cit.aet.artemis.service.tutorialgroups;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupFreePeriodRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupSessionRepository;

@Profile(PROFILE_CORE)
@Service
public class TutorialGroupFreePeriodService {

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    public TutorialGroupFreePeriodService(TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupFreePeriodRepository = tutorialGroupFreePeriodRepository;
    }

    /**
     * Find the first free period that overlaps with the given tutorial group session.
     * We only use the first overlapping period, because there can only be one freePeriod associated with a tutorialGroupSession.
     * There can only be one freePeriod associated with a tutorialGroupSession, as there would be a conflict in the displayed reason.
     *
     * @param course               the course in which the free period is defined
     * @param tutorialGroupSession the tutorial group session
     * @return the first free period that overlaps with the given tutorial group session
     */
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
        Set<TutorialGroupSession> sessionsToCancel = getActiveSessionsFromSet(overlappingSessions);

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
     * @param course                  the course in which the free period is defined
     * @param tutorialGroupFreePeriod the free period
     * @param updatedFreePeriod       the new value of the updated free period
     * @param onDeletion              true if the free period is deleted, false otherwise
     */
    public void updateOverlappingSessions(Course course, TutorialGroupFreePeriod tutorialGroupFreePeriod, TutorialGroupFreePeriod updatedFreePeriod, boolean onDeletion) {
        Set<TutorialGroupSession> overlappingSessions = tutorialGroupSessionRepository.findAllBetween(course, tutorialGroupFreePeriod.getStart(), tutorialGroupFreePeriod.getEnd());

        findAndUpdateStillCanceledSessions(course, tutorialGroupFreePeriod, overlappingSessions, updatedFreePeriod, onDeletion);
        findAndUpdateSessionsToReactivate(course, tutorialGroupFreePeriod, overlappingSessions);
    }

    /**
     * Find and update tutorial group sessions that are still cancelled due to overlapping with another free period.
     * If the Period that provided the reason initially is still present, the reason remains the same.
     * If it is not, it finds the first overlapping free period and updates the session status, status explanation, and the tutorial group free period.
     *
     * @param course                  the course in which the free period is defined.
     * @param tutorialGroupFreePeriod the free period.
     * @param overlappingSessions     the set of sessions that overlap with the given free period.
     * @param updatedFreePeriod       the new value of the updated free period
     * @param onDeletion              true if the free period is deleted, false otherwise.
     */
    private void findAndUpdateStillCanceledSessions(Course course, TutorialGroupFreePeriod tutorialGroupFreePeriod, Set<TutorialGroupSession> overlappingSessions,
            TutorialGroupFreePeriod updatedFreePeriod, boolean onDeletion) {
        overlappingSessions.forEach(session -> {
            Set<TutorialGroupFreePeriod> overlappingFreePeriods = tutorialGroupFreePeriodRepository.findOverlappingInSameCourseExclusive(course, session.getStart(),
                    session.getEnd());
            // if there is only one overlapping FreePeriod, the session is not still canceled
            // if the tutorialFreePeriod is not the same as the TutorialFreePeriod that caused the session to be canceled initially, we don't update the status and the explanation
            if (overlappingFreePeriods.size() <= 1 || !session.getTutorialGroupFreePeriod().equals(tutorialGroupFreePeriod)) {
                return;
            }

            if (onDeletion && session.getTutorialGroupFreePeriod().equals(tutorialGroupFreePeriod)) {
                TutorialGroupFreePeriod replacementFreePeriod = tutorialGroupFreePeriodRepository.findOverlappingInSameCourse(course, session.getStart(), session.getEnd()).stream()
                        .filter(period -> !period.equals(tutorialGroupFreePeriod)).findFirst()
                        // this should never happen, because we already checked that there is at least one other FreePeriod
                        .orElseThrow(() -> new IllegalStateException("No replacement FreePeriod found"));
                session.setStatus(TutorialGroupSessionStatus.CANCELLED);
                session.setStatusExplanation(null);
                session.setTutorialGroupFreePeriod(replacementFreePeriod);
            }

            // if the updated tutorialFreePeriod still overlaps with the session, we don't update the status and the explanation
            else if (updatedFreePeriod != null && session.getStart().isBefore(updatedFreePeriod.getEnd()) && session.getEnd().isAfter(updatedFreePeriod.getStart())) {
                return;
            }

            // the replacementFreePeriod is the first FreePeriod that overlaps with the session, but is not the FreePeriod that caused the session to be canceled initially
            Optional<TutorialGroupFreePeriod> freePeriod = overlappingFreePeriods.stream().filter(replacementFreePeriod -> !replacementFreePeriod.equals(tutorialGroupFreePeriod))
                    .findFirst();
            if (!onDeletion && freePeriod.isPresent() && session.getTutorialGroupFreePeriod().equals(tutorialGroupFreePeriod)) {
                session.setStatus(TutorialGroupSessionStatus.CANCELLED);
                session.setStatusExplanation(null);
                session.setTutorialGroupFreePeriod(freePeriod.get());
            }

            // Update the session
            tutorialGroupSessionRepository.save(session);
        });
    }

    /**
     * Find and reactivate tutorial group sessions that were cancelled due to overlapping with a free period that is mow removed.
     *
     * @param course                  The course in which the free period is defined.
     * @param tutorialGroupFreePeriod The free period.
     * @param overlappingSessions     The set of sessions that overlap with the given free period.
     */
    private void findAndUpdateSessionsToReactivate(Course course, TutorialGroupFreePeriod tutorialGroupFreePeriod, Set<TutorialGroupSession> overlappingSessions) {
        // filter out those sessions that are still overlapping with another free period
        Set<TutorialGroupSession> sessionsToReactivate = overlappingSessions.stream().filter(session -> {
            Set<TutorialGroupFreePeriod> overlappingPeriods = tutorialGroupFreePeriodRepository
                    .findOverlappingInSameCourse(tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getCourse(), session.getStart(), session.getEnd());
            return overlappingPeriods.size() <= 1;
        }).collect(Collectors.toSet());

        sessionsToReactivate.forEach(session -> {
            session.setStatus(TutorialGroupSessionStatus.ACTIVE);
            session.setStatusExplanation(null);
            session.setTutorialGroupFreePeriod(null);
        });
        tutorialGroupSessionRepository.saveAll(sessionsToReactivate);
    }

    public Set<TutorialGroupSession> getActiveSessionsFromSet(Set<TutorialGroupSession> sessions) {
        return sessions.stream().filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE).collect(Collectors.toSet());
    }

}
