package de.tum.cit.aet.artemis.assessment.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.service.AssessmentDemoService;
import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * Lets the demo seeding create the demo content of the assessment module. Only delegates to {@link AssessmentDemoService}, which holds the seeding logic.
 */
@Controller
@Lazy
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class AssessmentDemoApi implements AbstractApi {

    private final AssessmentDemoService assessmentDemoService;

    public AssessmentDemoApi(AssessmentDemoService assessmentDemoService) {
        this.assessmentDemoService = assessmentDemoService;
    }

    /**
     * Lets the participant scores follow result changes right away, so that the results the demo exercises create get participant scores as well.
     */
    public void activateParticipantScores() {
        assessmentDemoService.activateParticipantScores();
    }

    /**
     * Creates the grading scale of the demo course if the course does not have one yet.
     *
     * @param course the demo course.
     */
    public void createDemoGradingScale(Course course) {
        assessmentDemoService.createDemoGradingScale(course);
    }

    /**
     * Creates the grading scale of the demo exam if the exam does not have one yet.
     *
     * @param exam the demo exam.
     */
    public void createDemoExamGradingScale(Exam exam) {
        assessmentDemoService.createDemoExamGradingScale(exam);
    }
}
