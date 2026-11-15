package de.tum.cit.aet.artemis.exam.api;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.dto.StudentExamSubmissionGateDTO;
import de.tum.cit.aet.artemis.exam.service.ExamSubmissionService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.dto.StudentParticipationSubmitTargetDTO;

@Conditional(ExamEnabled.class)
@Controller
@Lazy
public class ExamSubmissionApi extends AbstractExamApi {

    private final ExamSubmissionService examSubmissionService;

    public ExamSubmissionApi(ExamSubmissionService examSubmissionService) {
        this.examSubmissionService = examSubmissionService;
    }

    public boolean isAllowedToSubmitDuringExam(Exercise exercise, User user, boolean withGracePeriod) {
        return examSubmissionService.isAllowedToSubmitDuringExam(exercise, user, withGracePeriod);
    }

    public Optional<StudentExamSubmissionGateDTO> findSubmissionGateForUser(User user, Exam exam, long exerciseId) {
        return examSubmissionService.findSubmissionGateForUser(user, exam, exerciseId);
    }

    @Nullable
    public StudentParticipationSubmitTargetDTO preventMultipleSubmissions(Exercise exercise, Submission submission, User user) {
        return examSubmissionService.preventMultipleSubmissions(exercise, submission, user);
    }

    public void checkSubmissionAllowanceElseThrow(Exercise exercise, User currentUser) {
        examSubmissionService.checkSubmissionAllowanceElseThrow(exercise, currentUser);
    }
}
