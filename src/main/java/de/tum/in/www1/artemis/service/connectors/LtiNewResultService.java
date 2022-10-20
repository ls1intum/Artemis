package de.tum.in.www1.artemis.service.connectors;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;

@Service
public class LtiNewResultService {

    private final Logger log = LoggerFactory.getLogger(LtiNewResultService.class);

    private final LtiService ltiService;

    private final Lti13Service lti13Service;

    public LtiNewResultService(LtiService ltiService, Lti13Service lti13Service) {
        this.ltiService = ltiService;
        this.lti13Service = lti13Service;
    }

    /**
     * This method is pinged on new exercise results. It calls both the LTIService and the LTI13Service in case it is an online course.
     *
     * @param participation The exercise participation for which a new build result is available
     */
    public void onNewResult(StudentParticipation participation) {
        if (!participation.getExercise().getCourseViaExerciseGroupOrCourseMember().isOnlineCourse()) {
            return;
        }

        ltiService.onNewResult(participation);
        lti13Service.onNewResult(participation);
    }
}
