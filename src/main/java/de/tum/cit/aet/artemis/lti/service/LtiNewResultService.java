package de.tum.cit.aet.artemis.lti.service;

import static de.tum.cit.aet.artemis.core.config.Constants.LTI_ENABLED_PROPERTY_NAME;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@Lazy
@Service
@ConditionalOnProperty(value = LTI_ENABLED_PROPERTY_NAME, havingValue = "true")
public class LtiNewResultService {

    private final Lti13Service lti13Service;

    public LtiNewResultService(Lti13Service lti13Service) {
        this.lti13Service = lti13Service;
    }

    /**
     * This method is pinged on new exercise results. It calls LTI13Service in case it is an online course.
     *
     * @param participation The exercise participation for which a new build result is available
     */
    @Async
    public void onNewResult(StudentParticipation participation) {
        if (!participation.getExercise().getCourseViaExerciseGroupOrCourseMember().isOnlineCourse()) {
            return;
        }
        lti13Service.onNewResult(participation);
    }
}
