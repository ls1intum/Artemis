package de.tum.cit.aet.artemis.lti.api;

import java.util.Collection;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.lti.config.LtiEnabled;
import de.tum.cit.aet.artemis.lti.domain.LtiResourceLaunch;
import de.tum.cit.aet.artemis.lti.repository.Lti13ResourceLaunchRepository;
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

    public LtiApi(Lti13ResourceLaunchRepository lti13ResourceLaunchRepository, LtiService ltiService, LtiNewResultService ltiNewResultService,
            OnlineCourseConfigurationService onlineCourseConfigurationService) {
        this.lti13ResourceLaunchRepository = lti13ResourceLaunchRepository;
        this.ltiService = ltiService;
        this.ltiNewResultService = ltiNewResultService;
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
    }

    public void onNewResult(StudentParticipation participation) {
        ltiNewResultService.onNewResult(participation);
    }

    public boolean isLtiCreatedUser(User user) {
        return ltiService.isLtiCreatedUser(user);
    }

    public void createOnlineCourseConfiguration(Course course) {
        onlineCourseConfigurationService.createOnlineCourseConfiguration(course);
    }

    public Collection<LtiResourceLaunch> findByUserAndExercise(User user, Exercise exercise) {
        return lti13ResourceLaunchRepository.findByUserAndExercise(user, exercise);
    }
}
