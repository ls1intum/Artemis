package de.tum.in.www1.artemis.service.tutorialgroups;

import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.getFirstDateOfWeekDay;

import java.time.*;
import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSchedule;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupScheduleRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupSessionRepository;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.errors.ScheduleOverlapsWithSessionException;

@Service
public class TutorialGroupScheduleService {

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    private final TutorialGroupFreePeriodService tutorialGroupFreePeriodService;

    public TutorialGroupScheduleService(TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupScheduleRepository tutorialGroupScheduleRepository,
            TutorialGroupFreePeriodService tutorialGroupFreePeriodService) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupScheduleRepository = tutorialGroupScheduleRepository;
        this.tutorialGroupFreePeriodService = tutorialGroupFreePeriodService;
    }

    /**
     * Create a new schedule for the given tutorial group and creates all corresponding sessions
     *
     * @param tutorialGroupsConfiguration the course wide tutorial groups configuration
     * @param tutorialGroup               the tutorial group for which the schedule should be created
     * @param tutorialGroupSchedule       the schedule to create
     */
    public void saveScheduleAndGenerateScheduledSessions(TutorialGroupsConfiguration tutorialGroupsConfiguration, TutorialGroup tutorialGroup,
            TutorialGroupSchedule tutorialGroupSchedule) {
        // Generate Sessions
        var individualSessions = generateSessions(tutorialGroupsConfiguration, tutorialGroupSchedule);
        // check for overlap with existing individual sessions
        var overlappingIndividualSessions = findOverlappingExistingSessions(tutorialGroup, individualSessions);
        if (!overlappingIndividualSessions.isEmpty()) {
            throw new ScheduleOverlapsWithSessionException(overlappingIndividualSessions, ZoneId.of(tutorialGroupsConfiguration.getCourse().getTimeZone()));
        }
        tutorialGroupSchedule.setTutorialGroup(tutorialGroup);
        if (tutorialGroupSchedule.getId() != null) {
            tutorialGroupSessionRepository.deleteByTutorialGroupSchedule(tutorialGroupSchedule);
        }

        TutorialGroupSchedule savedSchedule = tutorialGroupScheduleRepository.save(tutorialGroupSchedule);
        for (var individualSession : individualSessions) {
            individualSession.setTutorialGroupSchedule(savedSchedule);
            individualSession.setTutorialGroup(savedSchedule.getTutorialGroup());
        }
        tutorialGroupSessionRepository.saveAll(individualSessions);
    }

    @NotNull
    private Set<TutorialGroupSession> findOverlappingExistingSessions(TutorialGroup tutorialGroup, List<TutorialGroupSession> individualSessions) {
        var overlappingIndividualSessions = new HashSet<TutorialGroupSession>();
        for (var individualSession : individualSessions) {
            var overlappingSession = tutorialGroupSessionRepository.findOverlappingIndividualSessionsInSameTutorialGroup(tutorialGroup, individualSession.getStart(),
                    individualSession.getEnd());
            if (!overlappingSession.isEmpty()) {
                overlappingIndividualSessions.addAll(overlappingSession);
            }
        }
        return overlappingIndividualSessions;
    }

    /**
     * Generates all individual sessions for the given schedule
     *
     * @param tutorialGroupsConfiguration the course wide tutorial groups configuration
     * @param tutorialGroupSchedule       the schedule for which the sessions should be generated
     * @return a list of all generated individual sessions
     */
    public List<TutorialGroupSession> generateSessions(TutorialGroupsConfiguration tutorialGroupsConfiguration, TutorialGroupSchedule tutorialGroupSchedule) {
        return this.generateSessions(tutorialGroupsConfiguration.getCourse(), tutorialGroupSchedule);
    }

    /**
     * Generates all individual sessions for the given schedule
     *
     * @param course                the course for which the sessions should be generated
     * @param tutorialGroupSchedule the schedule for which the sessions should be generated
     * @return a list of all generated individual sessions
     */
    public List<TutorialGroupSession> generateSessions(Course course, TutorialGroupSchedule tutorialGroupSchedule) {
        ZoneId timeZone = ZoneId.of(course.getTimeZone());
        List<TutorialGroupSession> sessions = new ArrayList<>();
        ZonedDateTime periodEnd = ZonedDateTime.of(LocalDate.parse(tutorialGroupSchedule.getValidToInclusive()), TutorialGroupDateUtil.END_OF_DAY, timeZone);

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

    @NotNull
    private TutorialGroupSession generateScheduledSession(Course course, TutorialGroupSchedule tutorialGroupSchedule, ZonedDateTime sessionStart, ZonedDateTime sessionEnd) {
        TutorialGroupSession session = new TutorialGroupSession();
        session.setStart(sessionStart);
        session.setEnd(sessionEnd);
        session.setTutorialGroupSchedule(tutorialGroupSchedule);
        session.setTutorialGroup(tutorialGroupSchedule.getTutorialGroup());

        var overlappingPeriod = tutorialGroupFreePeriodService.findOverlappingPeriod(course, session);
        if (overlappingPeriod.isPresent()) {
            session.setStatus(TutorialGroupSessionStatus.CANCELLED);
            session.setStatusExplanation(overlappingPeriod.get().getReason());
        }
        else {
            session.setStatus(TutorialGroupSessionStatus.ACTIVE);
        }
        session.setLocation(tutorialGroupSchedule.getLocation());
        return session;
    }

    /**
     * Update the schedule of the given tutorial group
     *
     * @param tutorialGroupsConfiguration the course wide tutorial groups configuration
     * @param tutorialGroup               the tutorial group for which the schedule should be updated
     * @param oldSchedule                 the old schedule of the tutorial group
     * @param newSchedule                 the new schedule of the tutorial group
     */
    public void updateSchedule(TutorialGroupsConfiguration tutorialGroupsConfiguration, TutorialGroup tutorialGroup, Optional<TutorialGroupSchedule> oldSchedule,
            Optional<TutorialGroupSchedule> newSchedule) {
        if (oldSchedule.isPresent() && newSchedule.isPresent()) { // update existing schedule -> delete all scheduled sessions and recreate using the new schedule
            updateAllSessionsToNewSchedule(tutorialGroupsConfiguration, tutorialGroup, oldSchedule.get(), newSchedule.get());
        }
        else if (oldSchedule.isPresent()) { // old schedule present but not new schedule -> delete old schedule
            tutorialGroupScheduleRepository.delete(oldSchedule.get());
        }
        else if (newSchedule.isPresent()) { // new schedule present but not old schedule -> create new schedule
            saveScheduleAndGenerateScheduledSessions(tutorialGroupsConfiguration, tutorialGroup, newSchedule.get());
        }
    }

    private void updateAllSessionsToNewSchedule(TutorialGroupsConfiguration tutorialGroupsConfiguration, TutorialGroup tutorialGroup, TutorialGroupSchedule oldSchedule,
            TutorialGroupSchedule newSchedule) {
        overrideScheduleProperties(newSchedule, oldSchedule);
        saveScheduleAndGenerateScheduledSessions(tutorialGroupsConfiguration, tutorialGroup, oldSchedule);
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
