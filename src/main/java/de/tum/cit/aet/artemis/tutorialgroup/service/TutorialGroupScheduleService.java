package de.tum.cit.aet.artemis.tutorialgroup.service;

import static de.tum.cit.aet.artemis.core.util.DateUtil.getFirstDateOfWeekDay;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupScheduleDTO;
import de.tum.cit.aet.artemis.tutorialgroup.exception.SessionOverlapsWithSessionException;
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
     * Rejects a schedule update that would make a retained session overlap an individually edited session. Read-only: it persists nothing,
     * so a caller can validate before performing its own writes and side effects.
     *
     * @param course         the course of the tutorial group
     * @param tutorialGroup  the tutorial group whose schedule is being updated
     * @param newScheduleDTO the requested schedule, or null if the schedule is being removed
     * @throws SessionOverlapsWithSessionException if applying the schedule would make a retained session overlap an individual session
     */
    public void validateScheduleUpdateDoesNotOverlapIndividualSessions(Course course, TutorialGroup tutorialGroup, TutorialGroupScheduleDTO newScheduleDTO) {
        if (newScheduleDTO == null) {
            return;
        }
        TutorialGroupSchedule oldSchedule = tutorialGroupScheduleRepository.findByTutorialGroup_Id(tutorialGroup.getId()).orElse(null);
        if (oldSchedule == null) {
            return;
        }
        TutorialGroupSchedule newSchedule = TutorialGroupScheduleDTO.toTutorialGroupSchedule(newScheduleDTO);
        if (oldSchedule.sameSchedule(newSchedule) || oldSchedule.onlyLocationChanged(newSchedule)) {
            return;
        }
        Set<Instant> retainedStarts = new HashSet<>();
        for (TutorialGroupSession session : tutorialGroupSessionRepository.findAllByScheduleId(oldSchedule.getId())) {
            retainedStarts.add(session.getStart().toInstant());
        }
        List<TutorialGroupSession> detachedSessions = tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroup.getId()).stream()
                .filter(session -> session.getTutorialGroupSchedule() == null).toList();
        if (retainedStarts.isEmpty() || detachedSessions.isEmpty()) {
            return;
        }
        Set<TutorialGroupSession> overlappingIndividualSessions = new HashSet<>();
        for (TutorialGroupSession generatedSession : generateSessionsForSchedule(course, newSchedule)) {
            if (retainedStarts.contains(generatedSession.getStart().toInstant())) {
                detachedSessions.stream().filter(detached -> overlaps(generatedSession, detached)).forEach(overlappingIndividualSessions::add);
            }
        }
        if (!overlappingIndividualSessions.isEmpty()) {
            throw new SessionOverlapsWithSessionException(overlappingIndividualSessions, ZoneId.of(course.getTimeZone()));
        }
    }

    /**
     * Saves the given schedule and brings its generated sessions in sync with it.
     * <p>
     * For a new schedule, all sessions are generated. For an existing one, generated sessions are matched to the current ones by start
     * instant: a match keeps its id and recorded attendance while its end and location follow the new schedule, and its free-period status
     * is recomputed from the new interval - except a manual cancellation, which is kept with no free-period association. Starts that no
     * longer occur are removed, and new starts are added. Sessions edited individually (detached from the schedule) are left untouched -
     * neither duplicated nor removed. A schedule change that would extend a retained session into an individually edited session is
     * rejected before anything is persisted, so no overlapping sessions are created.
     *
     * @param course                the course of the tutorial group
     * @param tutorialGroup         the tutorial group the schedule belongs to
     * @param tutorialGroupSchedule the schedule to save and generate or update the sessions for
     * @throws SessionOverlapsWithSessionException if applying the schedule would make a retained session overlap an individually edited session
     */
    public void saveScheduleAndGenerateScheduledSessions(Course course, TutorialGroup tutorialGroup, TutorialGroupSchedule tutorialGroupSchedule) {
        tutorialGroupSchedule.setTutorialGroup(tutorialGroup);
        boolean scheduleAlreadyExisted = tutorialGroupSchedule.getId() != null;

        // Individually edited sessions are detached from the schedule (schedule set to null) but keep their slot. A regenerated session
        // that overlaps one of them is left to the edited session instead of being added as a duplicate.
        List<TutorialGroupSession> detachedSessions = tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroup.getId()).stream()
                .filter(session -> session.getTutorialGroupSchedule() == null).toList();

        // Key by instant, not ZonedDateTime: a persisted start read back (in UTC) would not equal a freshly generated one in the course
        // zone even for the same moment, so a ZonedDateTime key would never match and nothing would be preserved. If two sessions share a
        // start (e.g. from overlapping saves), keep the one carrying recorded data as the match and remove the other.
        Map<Instant, TutorialGroupSession> existingSessionsByStart = new HashMap<>();
        List<TutorialGroupSession> sessionsToRemove = new ArrayList<>();
        if (scheduleAlreadyExisted) {
            for (TutorialGroupSession existingSession : tutorialGroupSessionRepository.findAllByScheduleId(tutorialGroupSchedule.getId())) {
                Instant start = existingSession.getStart().toInstant();
                TutorialGroupSession alreadyKept = existingSessionsByStart.putIfAbsent(start, existingSession);
                if (alreadyKept != null) {
                    if (hasRecordedData(existingSession) && !hasRecordedData(alreadyKept)) {
                        existingSessionsByStart.put(start, existingSession);
                        sessionsToRemove.add(alreadyKept);
                    }
                    else {
                        sessionsToRemove.add(existingSession);
                    }
                }
            }
        }

        List<TutorialGroupSession> generatedSessions = generateSessionsForSchedule(course, tutorialGroupSchedule);

        // Reject before persisting: extending a retained session into an individually edited session would create an overlap the
        // single-session endpoint forbids. Nothing is saved yet, so both sessions and their recorded attendance stay intact.
        Set<TutorialGroupSession> overlappingIndividualSessions = new HashSet<>();
        for (TutorialGroupSession generatedSession : generatedSessions) {
            if (existingSessionsByStart.containsKey(generatedSession.getStart().toInstant())) {
                detachedSessions.stream().filter(detached -> overlaps(generatedSession, detached)).forEach(overlappingIndividualSessions::add);
            }
        }
        if (!overlappingIndividualSessions.isEmpty()) {
            throw new SessionOverlapsWithSessionException(overlappingIndividualSessions, ZoneId.of(course.getTimeZone()));
        }

        tutorialGroupScheduleRepository.save(tutorialGroupSchedule);

        // A regenerated session that starts at the same time as an existing one is the same session, so keep its id and recorded
        // attendance and let its end and location follow the new schedule. Existing sessions whose start no longer occurs are removed.
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
            else if (detachedSessions.stream().noneMatch(detached -> overlaps(generatedSession, detached))) {
                sessionsToPersist.add(generatedSession);
            }
        }
        // Save kept/new sessions before deleting obsolete ones, so an interruption leaves extra sessions, never missing ones.
        tutorialGroupSessionRepository.saveAll(sessionsToPersist);
        sessionsToRemove.addAll(existingSessionsByStart.values());
        if (!sessionsToRemove.isEmpty()) {
            tutorialGroupSessionRepository.deleteAll(sessionsToRemove);
        }
    }

    /**
     * Whether two sessions overlap in time, so that only one of them should occupy the slot.
     *
     * @param first  the first session
     * @param second the second session
     * @return true if their intervals overlap
     */
    private static boolean overlaps(TutorialGroupSession first, TutorialGroupSession second) {
        return first.getStart().isBefore(second.getEnd()) && second.getStart().isBefore(first.getEnd());
    }

    /**
     * Whether a session carries data worth keeping when two sessions share a start.
     *
     * @param session the session to check
     * @return true if it has recorded attendance, a status explanation or a cancellation
     */
    private static boolean hasRecordedData(TutorialGroupSession session) {
        return session.getAttendanceCount() != null || session.getStatusExplanation() != null || session.getStatus() == TutorialGroupSessionStatus.CANCELLED;
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
