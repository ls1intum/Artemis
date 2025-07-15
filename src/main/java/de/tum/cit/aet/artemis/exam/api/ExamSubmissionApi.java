package de.tum.cit.aet.artemis.exam.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.service.ExamSubmissionService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;

@ConditionalOnProperty(name = "artemis.exam.enabled", havingValue = "true")
@Controller
public class ExamSubmissionApi extends AbstractExamApi {

    private final ExamSubmissionService examSubmissionService;

    public ExamSubmissionApi(ExamSubmissionService examSubmissionService) {
        this.examSubmissionService = examSubmissionService;
    }

    public boolean isAllowedToSubmitDuringExam(Exercise exercise, User user, boolean withGracePeriod) {
        return examSubmissionService.isAllowedToSubmitDuringExam(exercise, user, withGracePeriod);
    }

    public Submission preventMultipleSubmissions(Exercise exercise, Submission submission, User user) {
        return examSubmissionService.preventMultipleSubmissions(exercise, submission, user);
    }

    public void checkSubmissionAllowanceElseThrow(Exercise exercise, User currentUser) {
        examSubmissionService.checkSubmissionAllowanceElseThrow(exercise, currentUser);
    }
}
