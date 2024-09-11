package de.tum.cit.aet.artemis.service.tutorialgroups;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupFreePeriodRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupScheduleRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupSessionRepository;

@Profile(PROFILE_CORE)
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
