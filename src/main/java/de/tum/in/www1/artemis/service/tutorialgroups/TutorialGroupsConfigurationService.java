package de.tum.in.www1.artemis.service.tutorialgroups;

import java.util.ArrayList;
import java.util.Set;

import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreePeriod;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSchedule;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupFreePeriodRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupScheduleRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupSessionRepository;

@Service
public class TutorialGroupsConfigurationService {

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    private final TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    private final TutorialGroupScheduleService tutorialGroupScheduleService;

    public TutorialGroupsConfigurationService(TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository,
            TutorialGroupScheduleRepository tutorialGroupScheduleRepository, TutorialGroupScheduleService tutorialGroupScheduleService) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupFreePeriodRepository = tutorialGroupFreePeriodRepository;
        this.tutorialGroupScheduleRepository = tutorialGroupScheduleRepository;
        this.tutorialGroupScheduleService = tutorialGroupScheduleService;
    }

    /**
     * Update/Delete tutorial group entities when the user has requested a time zone change on the course
     *
     * @param course affected course
     */
    @Transactional // ok because of delete
    public void onTimeZoneUpdate(Course course) {
        // ToDo: Think about smarter way to handle time zone change then just deleting the entities

        Set<TutorialGroupFreePeriod> tutorialGroupFreePeriods = this.tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(course.getId());

        // delete all sessions and tutorial free periods of course
        tutorialGroupSessionRepository.deleteByTutorialGroupCourse(course);
        tutorialGroupFreePeriodRepository.deleteAll(tutorialGroupFreePeriods);

        // recreate schedules sessions with new time zone
        var schedules = tutorialGroupScheduleRepository.getAllByTutorialGroupCourse(course);
        var newSessions = new ArrayList<TutorialGroupSession>();
        for (TutorialGroupSchedule schedule : schedules) {
            var tutorialGroupConfiguration = new TutorialGroupsConfiguration();
            tutorialGroupConfiguration.setCourse(course);
            newSessions.addAll(tutorialGroupScheduleService.generateSessions(tutorialGroupConfiguration, schedule));
        }
        tutorialGroupSessionRepository.saveAll(newSessions);
    }
}
