package de.tum.in.www1.artemis.service;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TutorialGroup;
import de.tum.in.www1.artemis.domain.TutorialGroupSchedule;
import de.tum.in.www1.artemis.domain.TutorialGroupSession;
import de.tum.in.www1.artemis.repository.TutorialGroupRepository;
import de.tum.in.www1.artemis.repository.TutorialGroupScheduleRepository;
import de.tum.in.www1.artemis.repository.TutorialGroupSessionRepository;

@Service
public class TutorialGroupScheduleService {

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    private final TutorialGroupRepository tutorialGroupRepository;

    public TutorialGroupScheduleService(TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupScheduleRepository tutorialGroupScheduleRepository,
            TutorialGroupRepository tutorialGroupRepository) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupScheduleRepository = tutorialGroupScheduleRepository;
        this.tutorialGroupRepository = tutorialGroupRepository;
    }

    // ToDo: Think about if transaction is needed here. A lot of validation needed that time zone and so on exists and dates, times make sense :(
    public void save(TutorialGroup tutorialGroup, TutorialGroupSchedule tutorialGroupSchedule) {
        tutorialGroupSchedule.setTutorialGroup(tutorialGroup);
        TutorialGroupSchedule savedSchedule = tutorialGroupScheduleRepository.save(tutorialGroupSchedule);
        var individualSessions = generateSessions(savedSchedule);
        tutorialGroupSessionRepository.saveAll(individualSessions);
    }

    public void delete(TutorialGroupSchedule tutorialGroupSchedule) {
        tutorialGroupScheduleRepository.delete(tutorialGroupSchedule);
    }

    public List<TutorialGroupSession> generateSessions(TutorialGroupSchedule tutorialGroupSchedule) {
        ZoneId creationTimeZone = ZoneId.of(tutorialGroupSchedule.getTimeZone());
        LocalDate periodStart = LocalDate.parse(tutorialGroupSchedule.getValidFromInclusive());
        LocalDate periodEnd = LocalDate.parse(tutorialGroupSchedule.getValidToInclusive());

        List<TutorialGroupSession> sessions = new ArrayList<>();

        ZonedDateTime sessionStart = ZonedDateTime.of(getFirstDateOfWeekDay(periodStart, tutorialGroupSchedule.getDayOfWeek()),
                LocalTime.parse(tutorialGroupSchedule.getStartTime()), creationTimeZone);
        ZonedDateTime sessionEnd = ZonedDateTime.of(getFirstDateOfWeekDay(periodStart, tutorialGroupSchedule.getDayOfWeek()), LocalTime.parse(tutorialGroupSchedule.getEndTime()),
                creationTimeZone);

        while (sessionStart.toLocalDate().isBefore(periodEnd) || sessionStart.toLocalDate().isEqual(periodEnd)) {
            TutorialGroupSession session = new TutorialGroupSession();
            session.setStart(sessionStart.withZoneSameLocal(ZoneId.of("UTC")));
            session.setEnd(sessionEnd.withZoneSameLocal(ZoneId.of("UTC")));
            session.setTutorialGroupSchedule(tutorialGroupSchedule);
            session.setTutorialGroup(tutorialGroupSchedule.getTutorialGroup());
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

    public void update(TutorialGroupSchedule oldSchedule, TutorialGroupSchedule newSchedule) {
        delete(oldSchedule);
        save(oldSchedule.getTutorialGroup(), newSchedule);
    }
}
