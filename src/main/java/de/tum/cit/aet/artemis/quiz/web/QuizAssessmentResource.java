package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentService;
import de.tum.cit.aet.artemis.assessment.web.AssessmentResource;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizStatisticsService;

/**
 * REST controller for managing the results of quiz submissions.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.QUIZ_EVALUATION)
@RestController
@RequestMapping("api/quiz/")
public class QuizAssessmentResource extends AssessmentResource {

    private static final String ENTITY_NAME = "quizAssessment";

    private final QuizStatisticsService quizStatisticsService;

    public QuizAssessmentResource(AuthorizationCheckService authCheckService, UserRepository userRepository, ExerciseRepository exerciseRepository,
            AssessmentService assessmentService, ResultRepository resultRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, QuizStatisticsService quizStatisticsService) {
        super(ENTITY_NAME, authCheckService, userRepository, exerciseRepository, assessmentService, resultRepository, exampleSubmissionRepository, submissionRepository);
        this.quizStatisticsService = quizStatisticsService;
    }

    /**
     * DELETE participations/:participationId/quiz-submissions/:submissionId/results/:resultId : Delete a result of a quiz submission.
     * The submission itself, with the answers of the student, is kept.
     *
     * @param participationId the id of the participation the submission belongs to
     * @param submissionId    the id of the submission the result belongs to
     * @param resultId        the id of the result that should be deleted
     * @return 200 Ok response if the user is at least instructor of the exercise and the result belongs to the submission
     */
    @Override
    @DeleteMapping("participations/{participationId}/quiz-submissions/{submissionId}/results/{resultId}")
    @EnforceAtLeastStudent
    @ManualConfig
    public ResponseEntity<Void> deleteAssessment(@PathVariable Long participationId, @PathVariable Long submissionId, @PathVariable Long resultId) {
        return super.deleteAssessment(participationId, submissionId, resultId);
    }

    /**
     * The statistics of a quiz are calculated from its results, so open statistics pages have to reload.
     *
     * @param exercise the quiz exercise of the deleted result
     */
    @Override
    protected void onAssessmentDeleted(Exercise exercise) {
        quizStatisticsService.notifyStatisticsChanged(exercise.getId());
    }

}
