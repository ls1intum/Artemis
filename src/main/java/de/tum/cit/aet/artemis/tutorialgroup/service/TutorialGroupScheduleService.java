package de.tum.cit.aet.artemis.tutorialgroup.service;

import static de.tum.cit.aet.artemis.core.util.DateUtil.getFirstDateOfWeekDay;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.util.DateUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupScheduleRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupSessionRepository;

@Conditional(TutorialGroupEnabled.class)
@Lazy
@Service
public class TutorialGroupScheduleService {

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    private final TutorialGroupFreePeriodService tutorialGroupFreePeriodService;

    private final TutorialGroupRepository tutorialGroupRepository;

    public TutorialGroupScheduleService(TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupScheduleRepository tutorialGroupScheduleRepository,
            TutorialGroupFreePeriodService tutorialGroupFreePeriodService, TutorialGroupRepository tutorialGroupRepository) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupScheduleRepository = tutorialGroupScheduleRepository;
        this.tutorialGroupFreePeriodService = tutorialGroupFreePeriodService;
        this.tutorialGroupRepository = tutorialGroupRepository;
    }

    /**
     * Create a new schedule for the given tutorial group and creates all corresponding sessions
     *
     * @param course                the course of the tutorial group
     * @param tutorialGroup         the tutorial group for which the schedule should be created
     * @param tutorialGroupSchedule the schedule to create
     */
    public void saveScheduleAndGenerateScheduledSessions(Course course, TutorialGroup tutorialGroup, TutorialGroupSchedule tutorialGroupSchedule) {
        tutorialGroupSchedule.setTutorialGroup(tutorialGroup);
        boolean scheduleAlreadyExisted = tutorialGroupSchedule.getId() != null;
        // Key by instant, not ZonedDateTime: a persisted start read back (in UTC) would not equal a freshly generated one in the
        // course zone even for the same moment, so a ZonedDateTime key would never match and nothing would be preserved.
        Map<Instant, TutorialGroupSession> existingSessionsByStart = scheduleAlreadyExisted ? tutorialGroupSessionRepository.findAllByScheduleId(tutorialGroupSchedule.getId())
                .stream().collect(Collectors.toMap(session -> session.getStart().toInstant(), Function.identity(), (first, second) -> first)) : new HashMap<>();

        TutorialGroupSchedule savedSchedule = tutorialGroupScheduleRepository.save(tutorialGroupSchedule);
        List<TutorialGroupSession> generatedSessions = generateSessionsForSchedule(course, savedSchedule);
        if (!scheduleAlreadyExisted) {
            tutorialGroupSessionRepository.saveAll(generatedSessions);
            return;
        }
        // The schedule changed: a regenerated session that starts at the same time as an existing one is the same session, so keep its
        // id and recorded attendance and let its end and location follow the new schedule. Existing sessions whose start no longer
        // occurs are removed.
        List<TutorialGroupSession> sessionsToPersist = new ArrayList<>();
        for (TutorialGroupSession generatedSession : generatedSessions) {
            TutorialGroupSession existingSession = existingSessionsByStart.remove(generatedSession.getStart().toInstant());
            if (existingSession != null) {
                existingSession.setEnd(generatedSession.getEnd());
                existingSession.setLocation(generatedSession.getLocation());
                // The new interval may now overlap (or no longer overlap) a free period, so recompute the free-period status from the
                // generated session's lookup. updateStatusAndFreePeriod keeps a manual cancellation; the attendance is untouched.
                updateStatusAndFreePeriod(existingSession, Optional.ofNullable(generatedSession.getTutorialGroupFreePeriod()));
                sessionsToPersist.add(existingSession);
            }
            else {
                sessionsToPersist.add(generatedSession);
            }
        }
        // Save kept/new sessions before deleting obsolete ones, so an interruption leaves extra sessions, never missing ones.
        tutorialGroupSessionRepository.saveAll(sessionsToPersist);
        List<TutorialGroupSession> sessionsToRemove = List.copyOf(existingSessionsByStart.values());
        if (!sessionsToRemove.isEmpty()) {
            tutorialGroupSessionRepository.deleteAll(sessionsToRemove);
        }
    }

    /**
     * Generates all individual sessions for the given schedule
     *
     * @param course                the course for which the sessions should be generated
     * @param tutorialGroupSchedule the schedule for which the sessions should be generated
     * @return a list of all generated individual sessions
     */
    public List<TutorialGroupSession> generateSessionsForSchedule(Course course, TutorialGroupSchedule tutorialGroupSchedule) {
        ZoneId timeZone = ZoneId.of(course.getTimeZone());
        List<TutorialGroupSession> sessions = new ArrayList<>();
        ZonedDateTime periodEnd = ZonedDateTime.of(LocalDate.parse(tutorialGroupSchedule.getValidToInclusive()), DateUtil.END_OF_DAY, timeZone);

        // generate first session in the period (starting point of generation for other sessions)
        ZonedDateTime sessionStart = ZonedDateTime.of(getFirstDateOfWeekDay(LocalDate.parse(tutorialGroupSchedule.getValidFromInclusive()), tutorialGroupSchedule.getDayOfWeek()),
                LocalTime.parse(tutorialGroupSchedule.getStartTime()), timeZone);
        ZonedDateTime sessionEnd = ZonedDateTime.of(getFirstDateOfWeekDay(LocalDate.parse(tutorialGroupSchedule.getValidFromInclusive()), tutorialGroupSchedule.getDayOfWeek()),
                LocalTime.parse(tutorialGroupSchedule.getEndTime()), timeZone);

        while (sessionEnd.isBefore(periodEnd) || sessionEnd.isEqual(periodEnd)) {
            TutorialGroupSession session = generateScheduledSession(course, tutorialGroupSchedule, sessionStart, sessionEnd);
            sessions.add(session);
            // add desired number of weeks to the session start and end to find the next session
            sessionStart = sessionStart.plusWeeks(tutorialGroupSchedule.getRepetitionFrequency());
            sessionEnd = sessionEnd.plusWeeks(tutorialGroupSchedule.getRepetitionFrequency());
        }
        return sessions;
    }

    private TutorialGroupSession generateScheduledSession(Course course, TutorialGroupSchedule tutorialGroupSchedule, ZonedDateTime sessionStart, ZonedDateTime sessionEnd) {
        TutorialGroupSession session = new TutorialGroupSession();
        session.setStart(sessionStart);
        session.setEnd(sessionEnd);
        session.setTutorialGroupSchedule(tutorialGroupSchedule);
        session.setTutorialGroup(tutorialGroupSchedule.getTutorialGroup());

        var overlappingPeriod = tutorialGroupFreePeriodService.findOverlappingPeriod(course, session).stream().findFirst();
        updateStatusAndFreePeriod(session, overlappingPeriod);
        session.setLocation(tutorialGroupSchedule.getLocation());
        return session;
    }

    /**
     * Updates the status and associated free period of a tutorial group session based on the presence of an overlapping free period.
     *
     * @param newSession        the tutorial group session to be updated.
     * @param overlappingPeriod an Optional that may contain a TutorialGroupFreePeriod if there is an overlapping free period.
     */
    public static void updateStatusAndFreePeriod(TutorialGroupSession newSession, Optional<TutorialGroupFreePeriod> overlappingPeriod) {
        // A manual cancellation (CANCELLED without a free period) is kept with its explanation regardless of free-period overlap; only
        // the explicit activate action reverses it. Free-period and active sessions derive their status from the current overlap.
        boolean manuallyCancelled = newSession.getStatus() == TutorialGroupSessionStatus.CANCELLED && newSession.getTutorialGroupFreePeriod() == null;
        if (manuallyCancelled) {
            return;
        }
        if (overlappingPeriod.isPresent()) {
            newSession.setStatus(TutorialGroupSessionStatus.CANCELLED);
            // the status explanation is set to null, as it is specified in the TutorialGroupFreePeriod
            newSession.setStatusExplanation(null);
            newSession.setTutorialGroupFreePeriod(overlappingPeriod.get());
        }
        else {
            newSession.setStatus(TutorialGroupSessionStatus.ACTIVE);
            newSession.setStatusExplanation(null);
            newSession.setTutorialGroupFreePeriod(null);
        }
    }

    /**
     * Update the schedule of the given tutorial group if it is changed
     *
     * @param course        the course of the tutorial group
     * @param tutorialGroup the tutorial group for which the schedule should be updated
     * @param oldSchedule   the old schedule of the tutorial group
     * @param newSchedule   the new schedule of the tutorial group
     */
    public void updateScheduleAndSessionsIfChanged(Course course, TutorialGroup tutorialGroup, Optional<TutorialGroupSchedule> oldSchedule,
            Optional<TutorialGroupSchedule> newSchedule) {
        if (oldSchedule.isPresent() && newSchedule.isPresent()) {
            var oldS = oldSchedule.get();
            var newS = newSchedule.get();
            if (!oldS.sameSchedule(newS)) { // update existing schedule -> delete all scheduled sessions and recreate using the new schedule
                if (oldS.onlyLocationChanged(newS)) {
                    updateAllSessionsToNewLocation(oldS, newS.getLocation());
                    oldS.setLocation(newS.getLocation());
                    tutorialGroupScheduleRepository.save(oldS);
                }
                else {
                    updateAllSessionsToNewSchedule(course, tutorialGroup, oldS, newS);
                }
            }
        }
        else if (oldSchedule.isPresent()) {
            // disassociate tutorial group from old schedule and make the persistence context aware of it to avoid Hibernate exception
            tutorialGroup.setTutorialGroupSchedule(null);
            tutorialGroupRepository.save(tutorialGroup);
            tutorialGroupScheduleRepository.delete(oldSchedule.get()); // old schedule present but not new schedule -> delete old schedule
        }
        else {
            newSchedule.ifPresent(tutorialGroupSchedule -> saveScheduleAndGenerateScheduledSessions(course, tutorialGroup, tutorialGroupSchedule));
        }
    }

    private void updateAllSessionsToNewLocation(TutorialGroupSchedule oldSchedule, String newLocation) {
        var sessions = tutorialGroupSessionRepository.findAllByScheduleId(oldSchedule.getId());
        sessions.forEach(session -> session.setLocation(newLocation));
        tutorialGroupSessionRepository.saveAll(sessions);
    }

    private void updateAllSessionsToNewSchedule(Course course, TutorialGroup tutorialGroup, TutorialGroupSchedule oldSchedule, TutorialGroupSchedule newSchedule) {
        overrideScheduleProperties(newSchedule, oldSchedule);
        saveScheduleAndGenerateScheduledSessions(course, tutorialGroup, oldSchedule);
    }

    private static void overrideScheduleProperties(TutorialGroupSchedule sourceSchedule, TutorialGroupSchedule originalSchedule) {
        originalSchedule.setLocation(sourceSchedule.getLocation());
        originalSchedule.setDayOfWeek(sourceSchedule.getDayOfWeek());
        originalSchedule.setStartTime(sourceSchedule.getStartTime());
        originalSchedule.setEndTime(sourceSchedule.getEndTime());
        originalSchedule.setRepetitionFrequency(sourceSchedule.getRepetitionFrequency());
        originalSchedule.setValidFromInclusive(sourceSchedule.getValidFromInclusive());
        originalSchedule.setValidToInclusive(sourceSchedule.getValidToInclusive());
    }
}
