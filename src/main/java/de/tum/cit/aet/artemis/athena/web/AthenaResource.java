package de.tum.cit.aet.artemis.athena.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.athena.dto.ModelingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.ProgrammingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.TextFeedbackDTO;
import de.tum.cit.aet.artemis.athena.service.AthenaFeedbackSuggestionsService;
import de.tum.cit.aet.artemis.athena.service.AthenaModuleService;
import de.tum.cit.aet.artemis.athena.service.AthenaRepositoryExportService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ApiNotPresentException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.ResponseUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.repository.ModelingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.text.api.TextApi;
import de.tum.cit.aet.artemis.text.api.TextRepositoryApi;
import de.tum.cit.aet.artemis.text.api.TextSubmissionApi;

/**
 * REST controller for Athena feedback suggestions.
 */
@Profile(PROFILE_ATHENA)
@RestController
@RequestMapping("api/athena/")
public class AthenaResource {

    private static final Logger log = LoggerFactory.getLogger(AthenaResource.class);

    @Value("${artemis.athena.secret}")
    private String athenaSecret;

    private final CourseRepository courseRepository;

    private final Optional<TextRepositoryApi> textRepositoryApi;

    private final Optional<TextSubmissionApi> textSubmissionApi;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final AuthorizationCheckService authCheckService;

    private final AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService;

    private final AthenaRepositoryExportService athenaRepositoryExportService;

    private final AthenaModuleService athenaModuleService;

    /**
     * The AthenaResource provides an endpoint for the client to fetch feedback suggestions from Athena.
     */
    public AthenaResource(CourseRepository courseRepository, Optional<TextRepositoryApi> textRepositoryApi, Optional<TextSubmissionApi> textSubmissionApi,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ModelingExerciseRepository modelingExerciseRepository, ModelingSubmissionRepository modelingSubmissionRepository, AuthorizationCheckService authCheckService,
            AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService, AthenaRepositoryExportService athenaRepositoryExportService,
            AthenaModuleService athenaModuleService) {
        this.courseRepository = courseRepository;
        this.textRepositoryApi = textRepositoryApi;
        this.textSubmissionApi = textSubmissionApi;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.authCheckService = authCheckService;
        this.athenaFeedbackSuggestionsService = athenaFeedbackSuggestionsService;
        this.athenaRepositoryExportService = athenaRepositoryExportService;
        this.athenaModuleService = athenaModuleService;
    }

    @FunctionalInterface
    private interface FeedbackProvider<ExerciseType, SubmissionType, OutputType> {

        /**
         * Method to apply the (graded) feedback provider. Examples: AthenaFeedbackSuggestionsService::getTextFeedbackSuggestions,
         * AthenaFeedbackSuggestionsService::getProgrammingFeedbackSuggestions
         */
        List<OutputType> apply(ExerciseType exercise, SubmissionType submission, Boolean isGraded) throws NetworkingException;
    }

    private <ExerciseT extends Exercise, SubmissionT extends Submission, OutputT> ResponseEntity<List<OutputT>> getFeedbackSuggestions(long exerciseId, long submissionId,
            Function<Long, ExerciseT> exerciseFetcher, Function<Long, SubmissionT> submissionFetcher, FeedbackProvider<ExerciseT, SubmissionT, OutputT> feedbackProvider) {

        log.debug("REST call to get feedback suggestions for exercise {}, submission {}", exerciseId, submissionId);

        final var exercise = exerciseFetcher.apply(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        // Check if feedback suggestions are actually enabled
        if (!exercise.areFeedbackSuggestionsEnabled()) {
            throw new InternalServerErrorException("Feedback suggestions are not enabled for this exercise");
        }

        final var submission = submissionFetcher.apply(submissionId);

        try {
            return ResponseEntity.ok(feedbackProvider.apply(exercise, submission, true));
        }
        catch (NetworkingException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private ResponseEntity<List<String>> getAvailableModules(long courseId, ExerciseType exerciseType) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        log.debug("REST request to get available Athena modules for {} exercises in course {}", exerciseType.getExerciseTypeAsReadableString(), course.getTitle());

        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        try {
            List<String> modules = athenaModuleService.getAthenaModulesForCourse(course, exerciseType);
            return ResponseEntity.ok(modules);
        }
        catch (NetworkingException e) {
            throw new InternalServerErrorException("Could not fetch available Athena modules for " + exerciseType.getExerciseTypeAsReadableString() + " exercises");
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
        var api = textRepositoryApi.orElseThrow(() -> new ApiNotPresentException(TextApi.class, PROFILE_CORE));
        var submissionApi = textSubmissionApi.orElseThrow(() -> new ApiNotPresentException(TextSubmissionApi.class, PROFILE_CORE));

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
        return getFeedbackSuggestions(exerciseId, submissionId, modelingExerciseRepository::findByIdElseThrow, modelingSubmissionRepository::findByIdElseThrow,
                athenaFeedbackSuggestionsService::getModelingFeedbackSuggestions);
    }

    /**
     * GET courses/{courseId}/text-exercises/available-modules : Get all available Athena modules for a text exercise in the course
     *
     * @param courseId the id of the course the text exercise belongs to
     * @return 200 Ok if successful with the modules as body
     */
    @GetMapping("courses/{courseId}/text-exercises/available-modules")
    @EnforceAtLeastEditor
    public ResponseEntity<List<String>> getAvailableModulesForTextExercises(@PathVariable long courseId) {
        return this.getAvailableModules(courseId, ExerciseType.TEXT);
    }

    /**
     * GET courses/{courseId}/programming-exercises/available-modules : Get all available Athena modules for a programming exercise in the course
     *
     * @param courseId the id of the course the programming exercise belongs to
     * @return 200 Ok if successful with the modules as body
     */
    @GetMapping("courses/{courseId}/programming-exercises/available-modules")
    @EnforceAtLeastEditor
    public ResponseEntity<List<String>> getAvailableModulesForProgrammingExercises(@PathVariable long courseId) {
        return this.getAvailableModules(courseId, ExerciseType.PROGRAMMING);
    }

    /**
     * GET courses/{courseId}/modeling-exercises/available-modules : Get all available Athena modules for a modeling exercise in the course
     *
     * @param courseId the id of the course the modeling exercise belongs to
     * @return 200 Ok if successful with the modules as body
     */
    @GetMapping("courses/{courseId}/modeling-exercises/available-modules")
    @EnforceAtLeastEditor
    public ResponseEntity<List<String>> getAvailableModulesForModelingExercises(@PathVariable long courseId) {
        return this.getAvailableModules(courseId, ExerciseType.MODELING);
    }

    /**
     * Check if the given auth header is valid for Athena, otherwise throw an exception.
     *
     * @param auth the auth header value to check
     */
    private void checkAthenaSecret(String auth) {
        if (!auth.equals(athenaSecret)) {
            log.error("Athena secret does not match");
            throw new AccessForbiddenException("Athena secret does not match");
        }
    }

    /**
     * GET public/programming-exercises/:exerciseId/submissions/:submissionId/repository : Get the repository as a zip file download
     *
     * @param exerciseId   the id of the exercise the submission belongs to
     * @param submissionId the id of the submission to get the repository for
     * @param auth         the auth header value to check
     * @return 200 Ok with the zip file as body if successful
     */
    @GetMapping("public/programming-exercises/{exerciseId}/submissions/{submissionId}/repository")
    @EnforceNothing // We check the Athena secret instead
    @ManualConfig
    public ResponseEntity<Resource> getRepository(@PathVariable long exerciseId, @PathVariable long submissionId, @RequestHeader("Authorization") String auth) throws IOException {
        log.debug("REST call to get student repository for exercise {}, submission {}", exerciseId, submissionId);
        checkAthenaSecret(auth);
        return ResponseUtil.ok(athenaRepositoryExportService.exportRepository(exerciseId, submissionId, null));
    }

    /**
     * GET public/programming-exercises/:exerciseId/repository/template : Get the template repository as a zip file download
     *
     * @param exerciseId the id of the exercise
     * @param auth       the auth header value to check
     * @return 200 Ok with the zip file as body if successful
     */
    @GetMapping("public/programming-exercises/{exerciseId}/repository/template")
    @EnforceNothing // We check the Athena secret instead
    @ManualConfig
    public ResponseEntity<Resource> getTemplateRepository(@PathVariable long exerciseId, @RequestHeader("Authorization") String auth) throws IOException {
        log.debug("REST call to get template repository for exercise {}", exerciseId);
        checkAthenaSecret(auth);
        return ResponseUtil.ok(athenaRepositoryExportService.exportRepository(exerciseId, null, RepositoryType.TEMPLATE));
    }

    /**
     * GET public/programming-exercises/:exerciseId/repository/solution : Get the solution repository as a zip file download
     *
     * @param exerciseId the id of the exercise
     * @param auth       the auth header value to check
     * @return 200 Ok with the zip file as body if successful
     */
    @GetMapping("public/programming-exercises/{exerciseId}/repository/solution")
    @EnforceNothing // We check the Athena secret instead
    @ManualConfig
    public ResponseEntity<Resource> getSolutionRepository(@PathVariable long exerciseId, @RequestHeader("Authorization") String auth) throws IOException {
        log.debug("REST call to get solution repository for exercise {}", exerciseId);
        checkAthenaSecret(auth);
        return ResponseUtil.ok(athenaRepositoryExportService.exportRepository(exerciseId, null, RepositoryType.SOLUTION));
    }

    /**
     * GET public/programming-exercises/:exerciseId/repository/tests : Get the test repository as a zip file download
     *
     * @param exerciseId the id of the exercise
     * @param auth       the auth header value to check
     * @return 200 Ok with the zip file as body if successful
     */
    @GetMapping("public/programming-exercises/{exerciseId}/repository/tests")
    @EnforceNothing // We check the Athena secret instead
    @ManualConfig
    public ResponseEntity<Resource> getTestRepository(@PathVariable long exerciseId, @RequestHeader("Authorization") String auth) throws IOException {
        log.debug("REST call to get test repository for exercise {}", exerciseId);
        checkAthenaSecret(auth);
        return ResponseUtil.ok(athenaRepositoryExportService.exportRepository(exerciseId, null, RepositoryType.TESTS));
    }
}
