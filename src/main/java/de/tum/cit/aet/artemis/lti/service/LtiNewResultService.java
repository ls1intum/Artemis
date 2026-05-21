package de.tum.cit.aet.artemis.lti.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.lti.config.LtiEnabled;

@Lazy
@Service
@Conditional(LtiEnabled.class)
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
