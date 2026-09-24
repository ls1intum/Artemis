package de.tum.cit.aet.artemis.hyperion.web;

import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.codegeneration.HyperionCodeGenerationExecutionService;
import de.tum.cit.aet.artemis.hyperion.service.codegeneration.HyperionCodeGenerationJobService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for Hyperion Code Generation features.
 * Provides AI-powered code generation capabilities for programming exercises
 * supporting SOLUTION, TEMPLATE, and TESTS repository types.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@FeatureUsage(UserFeature.HYPERION_CODE_GENERATION)
@RestController
@RequestMapping("api/hyperion/")
public class HyperionCodeGenerationResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionCodeGenerationResource.class);

    private static final String ENTITY_NAME = "hyperionCodeGeneration";

    private static final int MAX_SELECTED_FEEDBACK_THREAD_IDS = 25;

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final HyperionCodeGenerationExecutionService codeGenerationExecutionService;

    private final HyperionCodeGenerationJobService codeGenerationJobService;

    public HyperionCodeGenerationResource(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            HyperionCodeGenerationExecutionService codeGenerationExecutionService, HyperionCodeGenerationJobService codeGenerationJobService) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.codeGenerationExecutionService = codeGenerationExecutionService;
        this.codeGenerationJobService = codeGenerationJobService;
    }

    /**
     * POST programming-exercises/{exerciseId}/generate-code: Start code generation asynchronously and return a job id.
     * Uses AI-powered iterative approach to generate, compile, and improve code based on build feedback.
     * Supports generation for SOLUTION, TEMPLATE, and TESTS repositories.
     * Uses websocket to stream progress and completion events.
     *
     * @param exerciseId the ID of the programming exercise
     * @param request    the request containing repository type
     * @return ResponseEntity with status 200 and the created job id
     */
    @PostMapping("programming-exercises/{exerciseId}/generate-code")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<CodeGenerationJobStartDTO> generateCode(@PathVariable long exerciseId, @Valid @RequestBody CodeGenerationRequestDTO request) {
        log.debug("REST request to generate code for programming exercise [{}] with repository type [{}]", exerciseId, request.repositoryType());
        validateGenerationRequest(exerciseId, request);
        ProgrammingExercise exercise = loadProgrammingExercise(exerciseId);
        User user = userRepository.getUserWithAuthorities();
        Long courseId = resolveCourseId(exercise);
        String jobId = codeGenerationJobService.startJob(user, exercise, courseId, request.repositoryType(), request.initialAutoGeneration(), request.selectedFeedbackThreadIds());
        log.info("Started code generation job [{}] for exercise [{}]", jobId, exerciseId);
        return ResponseEntity.ok(new CodeGenerationJobStartDTO(jobId, request.repositoryType()));
    }

    /**
     * GET programming-exercises/{exerciseId}/code-generation/active-job: Return the code generation job the requesting user
     * is currently running for the exercise, without starting one.
     * <p>
     * The editor asks this every time it opens and while it waits for a free generation slot. It is an endpoint of its own
     * rather than a flag on the generation request so that the feature usage report can tell those probes from the
     * generations they check on: counted together, an editor that is merely opened looks like code being generated.
     *
     * @param exerciseId the ID of the programming exercise
     * @return 200 with the active job, or 204 when there is none
     */
    @UsageInteraction(FeatureInteraction.AUTOMATIC)
    @GetMapping("programming-exercises/{exerciseId}/code-generation/active-job")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<CodeGenerationJobStartDTO> getActiveCodeGenerationJob(@PathVariable long exerciseId) {
        log.debug("REST request to get the active code generation job for programming exercise [{}]", exerciseId);
        validateExerciseId(exerciseId);
        ProgrammingExercise exercise = loadProgrammingExercise(exerciseId);
        User user = userRepository.getUserWithAuthorities();
        return codeGenerationJobService.getActiveJob(user, exercise).map(job -> ResponseEntity.ok(new CodeGenerationJobStartDTO(job.jobId(), job.repositoryType())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Validates the code generation request parameters.
     *
     * @param exerciseId the exercise ID to validate
     * @param request    the request DTO to validate
     * @throws BadRequestAlertException if validation fails
     */
    private void validateGenerationRequest(long exerciseId, CodeGenerationRequestDTO request) {
        validateExerciseId(exerciseId);
        validateRepositoryType(request.repositoryType());
        validateSelectedFeedbackThreadIds(request.selectedFeedbackThreadIds());
    }

    private void validateExerciseId(long exerciseId) {
        if (exerciseId <= 0) {
            throw new BadRequestAlertException("Exercise ID must be positive", ENTITY_NAME, "invalidExerciseId");
        }
    }

    private void validateRepositoryType(RepositoryType repositoryType) {
        if (repositoryType == null) {
            throw new BadRequestAlertException("Repository type is required", ENTITY_NAME, "missingRepositoryType");
        }
        if (!isSupportedRepositoryType(repositoryType)) {
            throw new BadRequestAlertException("Repository type not supported for code generation: " + repositoryType, ENTITY_NAME, "unsupportedRepositoryType");
        }
    }

    private void validateSelectedFeedbackThreadIds(List<Long> selectedFeedbackThreadIds) {
        if (selectedFeedbackThreadIds == null) {
            return;
        }
        if (selectedFeedbackThreadIds.size() > MAX_SELECTED_FEEDBACK_THREAD_IDS) {
            throw new BadRequestAlertException("Too many selected feedback thread ids", ENTITY_NAME, "tooManySelectedFeedbackThreadIds");
        }
        boolean hasInvalidThreadId = selectedFeedbackThreadIds.stream().anyMatch(threadId -> threadId == null || threadId <= 0);
        if (hasInvalidThreadId) {
            throw new BadRequestAlertException("Selected feedback thread ids must be positive", ENTITY_NAME, "invalidSelectedFeedbackThreadIds");
        }
    }

    /**
     * Loads the programming exercise with required associations.
     *
     * @param exerciseId the ID of the exercise to load
     * @return the loaded programming exercise
     * @throws BadRequestAlertException if exercise is not suitable for code generation
     */
    private ProgrammingExercise loadProgrammingExercise(long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);

        validateExerciseForGeneration(exercise);

        return exercise;
    }

    private static Long resolveCourseId(ProgrammingExercise exercise) {
        if (exercise == null) {
            return null;
        }

        try {
            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            return course != null ? course.getId() : null;
        }
        catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Validates that the exercise is suitable for code generation.
     *
     * @param exercise the exercise to validate
     * @throws BadRequestAlertException if exercise is not suitable
     */
    private void validateExerciseForGeneration(ProgrammingExercise exercise) {
        if (exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            throw new BadRequestAlertException("Code generation is only supported for Java exercises", ENTITY_NAME, "unsupportedProgrammingLanguage");
        }
    }

    /**
     * Checks if the repository type is supported for code generation.
     *
     * @param repositoryType the repository type to check
     * @return true if supported, false otherwise
     */
    private boolean isSupportedRepositoryType(RepositoryType repositoryType) {
        return repositoryType == RepositoryType.SOLUTION || repositoryType == RepositoryType.TEMPLATE || repositoryType == RepositoryType.TESTS;
    }
}
