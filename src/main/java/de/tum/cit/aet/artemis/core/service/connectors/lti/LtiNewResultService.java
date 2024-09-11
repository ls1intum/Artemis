package de.tum.cit.aet.artemis.core.service.connectors.lti;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@Service
@Profile("lti")
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
    public void onNewResult(StudentParticipation participation) {
        if (!participation.getExercise().getCourseViaExerciseGroupOrCourseMember().isOnlineCourse()) {
            return;
        }
        lti13Service.onNewResult(participation);
    }
}
