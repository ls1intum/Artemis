package de.tum.in.www1.artemis.service.tutorialgroups;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSchedule;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupScheduleRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupSessionRepository;

@Service
public class TutorialGroupScheduleService {

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    private final TutorialGroupFreeDayService tutorialGroupFreeDayService;

    public TutorialGroupScheduleService(TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupScheduleRepository tutorialGroupScheduleRepository,
            TutorialGroupRepository tutorialGroupRepository, TutorialGroupFreeDayService tutorialGroupFreeDayService) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupScheduleRepository = tutorialGroupScheduleRepository;
        this.tutorialGroupFreeDayService = tutorialGroupFreeDayService;
    }

    public void save(TutorialGroupsConfiguration tutorialGroupsConfiguration, TutorialGroup tutorialGroup, TutorialGroupSchedule tutorialGroupSchedule) {
        tutorialGroupSchedule.setTutorialGroup(tutorialGroup);
        TutorialGroupSchedule savedSchedule = tutorialGroupScheduleRepository.save(tutorialGroupSchedule);
        var individualSessions = generateSessions(tutorialGroupsConfiguration, savedSchedule);
        tutorialGroupSessionRepository.saveAllAndFlush(individualSessions);

    }

    public void delete(TutorialGroupSchedule tutorialGroupSchedule) {
        tutorialGroupScheduleRepository.delete(tutorialGroupSchedule);
    }

    public List<TutorialGroupSession> generateSessions(TutorialGroupsConfiguration tutorialGroupsConfiguration, TutorialGroupSchedule tutorialGroupSchedule) {
        ZoneId creationTimeZone = ZoneId.of(tutorialGroupsConfiguration.getTimeZone());
        LocalDate periodStart = tutorialGroupSchedule.getValidFromInclusive();
        LocalDate periodEnd = tutorialGroupSchedule.getValidToInclusive();
        LocalTime startTime = tutorialGroupSchedule.getStartTime();
        LocalTime endTime = tutorialGroupSchedule.getEndTime();

        List<TutorialGroupSession> sessions = new ArrayList<>();

        ZonedDateTime sessionStart = ZonedDateTime.of(getFirstDateOfWeekDay(periodStart, tutorialGroupSchedule.getDayOfWeek()), startTime, creationTimeZone);
        ZonedDateTime sessionEnd = ZonedDateTime.of(getFirstDateOfWeekDay(periodStart, tutorialGroupSchedule.getDayOfWeek()), endTime, creationTimeZone);

        while (sessionStart.toLocalDate().isBefore(periodEnd) || sessionStart.toLocalDate().isEqual(periodEnd)) {
            TutorialGroupSession session = new TutorialGroupSession();
            // save in UTC timezone for the database
            session.setStart(sessionStart.withZoneSameInstant(ZoneId.of("UTC")));
            session.setEnd(sessionEnd.withZoneSameInstant(ZoneId.of("UTC")));
            session.setTutorialGroupSchedule(tutorialGroupSchedule);
            session.setTutorialGroup(tutorialGroupSchedule.getTutorialGroup());

            if (!tutorialGroupFreeDayService.findOverlappingFreeDays(tutorialGroupsConfiguration.getCourse(), session).isEmpty()) {
                session.setStatus(TutorialGroupSessionStatus.CANCELLED);
                // Todo: Set Status reason
            }
            else {
                session.setStatus(TutorialGroupSessionStatus.ACTIVE);
            }

            sessions.add(session);

            sessionStart = sessionStart.plusWeeks(tutorialGroupSchedule.getRepetitionFrequency());
            sessionEnd = sessionEnd.plusWeeks(tutorialGroupSchedule.getRepetitionFrequency());
        }
        return sessions;
    }

    private LocalDate getFirstDateOfWeekDay(LocalDate start, Integer weekDay) {
        while (start.getDayOfWeek().getValue() != weekDay) {
            start = start.plusDays(1);
        }
        return start;
    }

    public void update(TutorialGroupsConfiguration tutorialGroupsConfiguration, TutorialGroupSchedule oldSchedule, TutorialGroupSchedule newSchedule) {
        delete(oldSchedule);
        save(tutorialGroupsConfiguration, oldSchedule.getTutorialGroup(), newSchedule);
    }
}
