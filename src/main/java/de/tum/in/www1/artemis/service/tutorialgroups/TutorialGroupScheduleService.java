package de.tum.in.www1.artemis.service.tutorialgroups;

import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.getFirstDateOfWeekDay;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSchedule;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupScheduleRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupSessionRepository;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil;

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

    public void saveAndGenerateScheduledSessions(TutorialGroupsConfiguration tutorialGroupsConfiguration, TutorialGroup tutorialGroup,
            TutorialGroupSchedule tutorialGroupSchedule) {
        tutorialGroupSchedule.setTutorialGroup(tutorialGroup);
        TutorialGroupSchedule savedSchedule = tutorialGroupScheduleRepository.save(tutorialGroupSchedule);
        var individualSessions = generateSessions(tutorialGroupsConfiguration, savedSchedule);
        tutorialGroupSessionRepository.saveAllAndFlush(individualSessions);
    }

    public List<TutorialGroupSession> generateSessions(TutorialGroupsConfiguration tutorialGroupsConfiguration, TutorialGroupSchedule tutorialGroupSchedule) {
        ZoneId timeZone = ZoneId.of(tutorialGroupsConfiguration.getTimeZone());
        List<TutorialGroupSession> sessions = new ArrayList<>();
        ZonedDateTime periodEnd = ZonedDateTime.of(tutorialGroupSchedule.getValidToInclusive(), TutorialGroupDateUtil.END_OF_DAY, timeZone);

        // generate first session in the period (starting point of generation for other sessions)
        ZonedDateTime sessionStart = ZonedDateTime.of(getFirstDateOfWeekDay(tutorialGroupSchedule.getValidFromInclusive(), tutorialGroupSchedule.getDayOfWeek()),
                LocalTime.parse(tutorialGroupSchedule.getStartTime()), timeZone);
        ZonedDateTime sessionEnd = ZonedDateTime.of(getFirstDateOfWeekDay(tutorialGroupSchedule.getValidFromInclusive(), tutorialGroupSchedule.getDayOfWeek()),
                LocalTime.parse(tutorialGroupSchedule.getEndTime()), timeZone);

        while (sessionEnd.isBefore(periodEnd) || sessionEnd.isEqual(periodEnd)) {
            TutorialGroupSession session = generateScheduledSession(tutorialGroupsConfiguration, tutorialGroupSchedule, sessionStart, sessionEnd);
            sessions.add(session);
            // add desired number of weeks to the session start and end to find the next session
            sessionStart = sessionStart.plusWeeks(tutorialGroupSchedule.getRepetitionFrequency());
            sessionEnd = sessionEnd.plusWeeks(tutorialGroupSchedule.getRepetitionFrequency());
        }
        return sessions;
    }

    @NotNull
    private TutorialGroupSession generateScheduledSession(TutorialGroupsConfiguration tutorialGroupsConfiguration, TutorialGroupSchedule tutorialGroupSchedule,
            ZonedDateTime sessionStart, ZonedDateTime sessionEnd) {
        TutorialGroupSession session = new TutorialGroupSession();
        session.setStart(sessionStart);
        session.setEnd(sessionEnd);
        session.setTutorialGroupSchedule(tutorialGroupSchedule);
        session.setTutorialGroup(tutorialGroupSchedule.getTutorialGroup());

        var overlappingPeriod = tutorialGroupFreePeriodService.findOverlappingPeriod(tutorialGroupsConfiguration.getCourse(), session);
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

    public void updateSchedule(TutorialGroupsConfiguration tutorialGroupsConfiguration, TutorialGroup tutorialGroup, Optional<TutorialGroupSchedule> oldSchedule,
            Optional<TutorialGroupSchedule> newSchedule) {
        if (oldSchedule.isPresent() && newSchedule.isPresent()) { // update existing schedule
            var schedule = oldSchedule.get();
            tutorialGroupSessionRepository.deleteByTutorialGroupSchedule(schedule);
            overrideValues(newSchedule.get(), schedule);
            saveAndGenerateScheduledSessions(tutorialGroupsConfiguration, tutorialGroup, schedule);
        }
        else if (oldSchedule.isPresent()) { // old schedule present but not new schedule -> delete old schedule
            tutorialGroupScheduleRepository.delete(oldSchedule.get());
        }
        else if (newSchedule.isPresent()) { // new schedule present but not old schedule -> create new schedule
            saveAndGenerateScheduledSessions(tutorialGroupsConfiguration, tutorialGroup, newSchedule.get());
        }
    }

    private static void overrideValues(TutorialGroupSchedule sourceSchedule, TutorialGroupSchedule originalSchedule) {
        originalSchedule.setLocation(sourceSchedule.getLocation());
        originalSchedule.setDayOfWeek(sourceSchedule.getDayOfWeek());
        originalSchedule.setStartTime(sourceSchedule.getStartTime());
        originalSchedule.setEndTime(sourceSchedule.getEndTime());
        originalSchedule.setRepetitionFrequency(sourceSchedule.getRepetitionFrequency());
        originalSchedule.setValidFromInclusive(sourceSchedule.getValidFromInclusive());
        originalSchedule.setValidToInclusive(sourceSchedule.getValidToInclusive());
    }
}
