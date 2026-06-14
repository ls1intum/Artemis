package de.tum.cit.aet.artemis.athena.web;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.athena.config.AthenaEnabled;
import de.tum.cit.aet.artemis.athena.config.AthenaHealthIndicator;
import de.tum.cit.aet.artemis.athena.dto.ModelingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.ProgrammingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.TextFeedbackDTO;
import de.tum.cit.aet.artemis.athena.service.AthenaFeedbackSuggestionsService;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.modeling.api.ModelingRepositoryApi;
import de.tum.cit.aet.artemis.modeling.api.ModelingSubmissionApi;
import de.tum.cit.aet.artemis.modeling.config.ModelingApiNotPresentException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.text.api.TextApi;
import de.tum.cit.aet.artemis.text.api.TextRepositoryApi;
import de.tum.cit.aet.artemis.text.api.TextSubmissionApi;
import de.tum.cit.aet.artemis.text.config.TextApiNotPresentException;

/**
 * REST controller for Athena feedback suggestions.
 */
@Conditional(AthenaEnabled.class)
@Lazy
@RestController
@RequestMapping("api/athena/")
public class AthenaResource {

    private static final Logger log = LoggerFactory.getLogger(AthenaResource.class);

    private final UserRepository userRepository;

    private final Optional<TextRepositoryApi> textRepositoryApi;

    private final Optional<TextSubmissionApi> textSubmissionApi;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final Optional<ModelingRepositoryApi> modelingRepositoryApi;

    private final Optional<ModelingSubmissionApi> modelingSubmissionApi;

    private final AuthorizationCheckService authCheckService;

    private final AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService;

    private final AthenaHealthIndicator athenaHealthIndicator;

    /**
     * The AthenaResource provides an endpoint for the client to fetch feedback suggestions from Athena.
     */
    public AthenaResource(UserRepository userRepository, Optional<TextRepositoryApi> textRepositoryApi, Optional<TextSubmissionApi> textSubmissionApi,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            Optional<ModelingRepositoryApi> modelingRepositoryApi, Optional<ModelingSubmissionApi> modelingSubmissionApi, AuthorizationCheckService authCheckService,
            AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService, AthenaHealthIndicator athenaHealthIndicator) {
        this.userRepository = userRepository;
        this.textRepositoryApi = textRepositoryApi;
        this.textSubmissionApi = textSubmissionApi;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.modelingRepositoryApi = modelingRepositoryApi;
        this.modelingSubmissionApi = modelingSubmissionApi;
        this.authCheckService = authCheckService;
        this.athenaFeedbackSuggestionsService = athenaFeedbackSuggestionsService;
        this.athenaHealthIndicator = athenaHealthIndicator;
    }

    /**
     * GET athena/health : Check if the Athena service is available.
     *
     * @return 200 Ok with true if Athena is healthy, false otherwise
     */
    @GetMapping("health")
    @EnforceAtLeastStudent
    public ResponseEntity<Boolean> checkAthenaHealth() {
        return ResponseEntity.ok(athenaHealthIndicator.isHealthy());
    }

    @FunctionalInterface
    private interface FeedbackProvider<ExerciseType, SubmissionType, OutputType> {

        /**
         * Method to apply the (graded) feedback provider. Examples: AthenaFeedbackSuggestionsService::getTextFeedbackSuggestions,
         * AthenaFeedbackSuggestionsService::getProgrammingFeedbackSuggestions
         */
        List<OutputType> apply(ExerciseType exercise, SubmissionType submission, boolean isGraded, User user) throws NetworkingException;
    }

    private <ExerciseT extends Exercise, SubmissionT extends Submission, OutputT> ResponseEntity<List<OutputT>> getFeedbackSuggestions(long exerciseId, long submissionId,
            Function<Long, ExerciseT> exerciseFetcher, Function<Long, SubmissionT> submissionFetcher, FeedbackProvider<ExerciseT, SubmissionT, OutputT> feedbackProvider) {

        log.debug("REST call to get feedback suggestions for exercise {}, submission {}", exerciseId, submissionId);

        final var exercise = exerciseFetcher.apply(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (course.getAthenaConfig() == null || !course.getAthenaConfig().isGradingFeedbackEnabled()) {
            throw new InternalServerErrorException("Athena grading feedback is not enabled for this course");
        }

        final var submission = submissionFetcher.apply(submissionId);
        final var user = userRepository.getUser();

        try {
            return ResponseEntity.ok(feedbackProvider.apply(exercise, submission, true, user));
        }
        catch (NetworkingException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * GET text-exercises/:exerciseId/submissions/:submissionId/feedback-suggestions : Get feedback suggestions from Athena for a text exercise
     *
     * @param exerciseId   the id of the exercise the submission belongs to
     * @param submissionId the id of the submission to get feedback suggestions for
     * @return 200 Ok if successful with the corresponding result as body
     */
    @GetMapping("text-exercises/{exerciseId}/submissions/{submissionId}/feedback-suggestions")
    @EnforceAtLeastTutor
    public ResponseEntity<List<TextFeedbackDTO>> getTextFeedbackSuggestions(@PathVariable long exerciseId, @PathVariable long submissionId) {
        var api = textRepositoryApi.orElseThrow(() -> new TextApiNotPresentException(TextApi.class));
        var submissionApi = textSubmissionApi.orElseThrow(() -> new TextApiNotPresentException(TextSubmissionApi.class));

        return getFeedbackSuggestions(exerciseId, submissionId, api::findByIdElseThrow, submissionApi::findByIdElseThrow,
                athenaFeedbackSuggestionsService::getTextFeedbackSuggestions);
    }

    /**
     * GET programming-exercises/:exerciseId/submissions/:submissionId/feedback-suggestions : Get feedback suggestions from Athena for a programming exercise
     *
     * @param exerciseId   the id of the exercise the submission belongs to
     * @param submissionId the id of the submission to get feedback suggestions for
     * @return 200 Ok if successful with the corresponding result as body
     */
    @GetMapping("programming-exercises/{exerciseId}/submissions/{submissionId}/feedback-suggestions")
    @EnforceAtLeastTutor
    public ResponseEntity<List<ProgrammingFeedbackDTO>> getProgrammingFeedbackSuggestions(@PathVariable long exerciseId, @PathVariable long submissionId) {
        return getFeedbackSuggestions(exerciseId, submissionId, programmingExerciseRepository::findByIdElseThrow, programmingSubmissionRepository::findByIdElseThrow,
                athenaFeedbackSuggestionsService::getProgrammingFeedbackSuggestions);
    }

    /**
     * GET modeling-exercises/:exerciseId/submissions/:submissionId/feedback-suggestions : Get feedback suggestions from Athena for a modeling exercise
     *
     * @param exerciseId   the id of the exercise the submission belongs to
     * @param submissionId the id of the submission to get feedback suggestions for
     * @return 200 Ok if successful with the corresponding result as body
     */
    @GetMapping("modeling-exercises/{exerciseId}/submissions/{submissionId}/feedback-suggestions")
    @EnforceAtLeastTutor
    public ResponseEntity<List<ModelingFeedbackDTO>> getModelingFeedbackSuggestions(@PathVariable long exerciseId, @PathVariable long submissionId) {
        var exerciseApi = modelingRepositoryApi.orElseThrow(() -> new ModelingApiNotPresentException(ModelingRepositoryApi.class));
        var submissionApi = modelingSubmissionApi.orElseThrow(() -> new ModelingApiNotPresentException(ModelingSubmissionApi.class));

        return getFeedbackSuggestions(exerciseId, submissionId, exerciseApi::findByIdElseThrow, submissionApi::findByIdElseThrow,
                athenaFeedbackSuggestionsService::getModelingFeedbackSuggestions);
    }

}
