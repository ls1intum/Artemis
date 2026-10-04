package de.tum.cit.aet.artemis.lti.api;

import java.util.Collection;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.lti.config.LtiEnabled;
import de.tum.cit.aet.artemis.lti.domain.LtiResourceLaunch;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.lti.repository.Lti13ResourceLaunchRepository;
import de.tum.cit.aet.artemis.lti.repository.OnlineCourseConfigurationRepository;
import de.tum.cit.aet.artemis.lti.service.LtiNewResultService;
import de.tum.cit.aet.artemis.lti.service.LtiService;
import de.tum.cit.aet.artemis.lti.service.OnlineCourseConfigurationService;

@Conditional(LtiEnabled.class)
@Controller
@Lazy
public class LtiApi extends AbstractLtiApi {

    private final Lti13ResourceLaunchRepository lti13ResourceLaunchRepository;

    private final LtiService ltiService;

    private final LtiNewResultService ltiNewResultService;

    private final OnlineCourseConfigurationService onlineCourseConfigurationService;

    private final OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    public LtiApi(Lti13ResourceLaunchRepository lti13ResourceLaunchRepository, LtiService ltiService, LtiNewResultService ltiNewResultService,
            OnlineCourseConfigurationService onlineCourseConfigurationService, OnlineCourseConfigurationRepository onlineCourseConfigurationRepository) {
        this.lti13ResourceLaunchRepository = lti13ResourceLaunchRepository;
        this.ltiService = ltiService;
        this.ltiNewResultService = ltiNewResultService;
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
        this.onlineCourseConfigurationRepository = onlineCourseConfigurationRepository;
    }

    public void onNewResult(StudentParticipation participation) {
        ltiNewResultService.onNewResult(participation);
    }

    public boolean isLtiCreatedUser(User user) {
        return ltiService.isLtiCreatedUser(user);
    }

    public boolean needsInitialization(User user) {
        return ltiService.needsInitialization(user);
    }

    public boolean claimInitialization(User user) {
        return ltiService.claimInitialization(user);
    }

    /**
     * Creates and stores the configuration of an online course. The course has to be stored already, because the
     * configuration holds the key to it.
     *
     * @param course the stored online course
     * @return the stored configuration
     */
    public OnlineCourseConfiguration createOnlineCourseConfiguration(Course course) {
        return onlineCourseConfigurationService.createOnlineCourseConfiguration(course);
    }

    /**
     * Reads the online course configuration of a course. A course does not carry it, so this is the one way to get it.
     *
     * @param courseId the id of the course
     * @return the configuration, or empty when the course has none
     */
    public Optional<OnlineCourseConfiguration> findOnlineCourseConfiguration(long courseId) {
        return onlineCourseConfigurationRepository.findByCourseId(courseId);
    }

    /**
     * Removes the online course configuration of a course, e.g. when it stops being an online course.
     *
     * @param courseId the id of the course
     */
    public void deleteOnlineCourseConfiguration(long courseId) {
        onlineCourseConfigurationRepository.deleteByCourseId(courseId);
    }

    public Collection<LtiResourceLaunch> findByUserAndExercise(User user, Exercise exercise) {
        return lti13ResourceLaunchRepository.findByUserAndExercise(user, exercise);
    }
}
