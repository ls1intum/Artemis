package de.tum.in.www1.artemis.service.connectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.participation.StudentParticipation;

@Service
public class LtiNewResultService {

    private final Lti10Service lti10Service;

    private final Lti13Service lti13Service;

    public LtiNewResultService(Lti10Service lti10Service, Lti13Service lti13Service) {
        this.lti10Service = lti10Service;
        this.lti13Service = lti13Service;
    }

    /**
     * This method is pinged on new exercise results. It calls both the LTI10Service and the LTI13Service in case it is an online course.
     *
     * @param participation The exercise participation for which a new build result is available
     */
    public void onNewResult(StudentParticipation participation) {
        if (!participation.getExercise().getCourseViaExerciseGroupOrCourseMember().isOnlineCourse()) {
            return;
        }

        lti10Service.onNewResult(participation);
        lti13Service.onNewResult(participation);
    }
}
