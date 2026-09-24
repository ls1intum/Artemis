package de.tum.cit.aet.artemis.tutorialgroup.service;

import java.util.ArrayList;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupFreePeriodRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupScheduleRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupSessionRepository;

@Conditional(TutorialGroupEnabled.class)
@Lazy
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
     * <p>
     * Sessions and free periods are absolute instants, so a change of the course time zone invalidates both: they are
     * discarded and the sessions regenerated from the schedules, which are stored as local dates and times.
     * <p>
     * The deletes must precede the generation and not the other way round, because
     * {@link TutorialGroupScheduleService#generateSessionsForSchedule} looks up the free period overlapping each
     * session it builds. Generating first would attach the new sessions to free periods that are about to be removed.
     * <p>
     * This runs without a transaction spanning the calls, in line with the rule that transaction boundaries belong in
     * repositories. The consequence is bounded rather than corrupting: a failure after the deletes leaves the course
     * with no tutorial group sessions, and because everything is derived from the schedules, running this again
     * produces exactly the same result. Anything that has to be read before the deletes is therefore read up front,
     * which keeps that window down to the generation itself.
     * <p>
     * <b>Being idempotent is not the same as recovering by itself.</b> The time zone is already committed by the time
     * this runs, so the caller that detects the change will not detect it again: re-sending the same course no longer
     * reaches this method, and the sessions stay missing until something else triggers a regeneration. Restoring them
     * means changing the time zone to another value and back, which does reach this method. Closing the window
     * properly needs the delete and the regeneration under one repository-owned transaction; that is recorded as a
     * follow-up rather than done here, because it is a different change from removing the boundaries.
     *
     * @param course affected course
     */
    public void onTimeZoneUpdate(Course course) {
        // ToDo: Think about smarter way to handle time zone change then just deleting the entities

        Set<TutorialGroupFreePeriod> tutorialGroupFreePeriods = this.tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(course.getId());
        // Read before deleting: the schedules are the only input the regeneration below needs, and they are untouched
        // by the deletes.
        var schedules = tutorialGroupScheduleRepository.getAllByTutorialGroupCourse(course);

        // delete all sessions and tutorial free periods of course
        tutorialGroupSessionRepository.deleteByTutorialGroupCourse(course);
        tutorialGroupFreePeriodRepository.deleteAll(tutorialGroupFreePeriods);

        // recreate schedules sessions with new time zone
        var newSessions = new ArrayList<TutorialGroupSession>();
        for (TutorialGroupSchedule schedule : schedules) {
            newSessions.addAll(tutorialGroupScheduleService.generateSessionsForSchedule(course, schedule));
        }
        tutorialGroupSessionRepository.saveAll(newSessions);
    }
}
