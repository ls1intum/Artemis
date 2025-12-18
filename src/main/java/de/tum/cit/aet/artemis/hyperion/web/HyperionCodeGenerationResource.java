package de.tum.cit.aet.artemis.hyperion.web;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.codegeneration.HyperionCodeGenerationExecutionService;
import de.tum.cit.aet.artemis.hyperion.service.codegeneration.HyperionCodeGenerationJobService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for Hyperion Code Generation features.
 * Provides AI-powered code generation capabilities for programming exercises
 * supporting SOLUTION, TEMPLATE, and TESTS repository types.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@RestController
@RequestMapping("api/hyperion/")
public class HyperionCodeGenerationResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionCodeGenerationResource.class);

    private static final String ENTITY_NAME = "hyperionCodeGeneration";

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
        User user = userRepository.getUserWithGroupsAndAuthorities();
        String jobId = codeGenerationJobService.startJob(user, exercise, request.repositoryType());
        log.info(ResponseEntity.ok(new CodeGenerationJobStartDTO(jobId)).toString());
        return ResponseEntity.ok(new CodeGenerationJobStartDTO(jobId));
    }

    /**
     * Validates the code generation request parameters.
     *
     * @param exerciseId the exercise ID to validate
     * @param request    the request DTO to validate
     * @throws BadRequestAlertException if validation fails
     */
    private void validateGenerationRequest(long exerciseId, CodeGenerationRequestDTO request) {
        if (exerciseId <= 0) {
            throw new BadRequestAlertException("Exercise ID must be positive", ENTITY_NAME, "invalidExerciseId");
        }

        if (request.repositoryType() == null) {
            throw new BadRequestAlertException("Repository type is required", ENTITY_NAME, "missingRepositoryType");
        }

        if (!isSupportedRepositoryType(request.repositoryType())) {
            throw new BadRequestAlertException("Repository type not supported for code generation: " + request.repositoryType(), ENTITY_NAME, "unsupportedRepositoryType");
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

    /**
     * Validates that the exercise is suitable for code generation.
     *
     * @param exercise the exercise to validate
     * @throws BadRequestAlertException if exercise is not suitable
     */
    private void validateExerciseForGeneration(ProgrammingExercise exercise) {
        if (exercise.isExamExercise()) {
            log.debug("Generating code for exam exercise [{}]", exercise.getId());
        }

        if (exercise.getBuildConfig() == null) {
            throw new BadRequestAlertException("Exercise must have build configuration for code generation", ENTITY_NAME, "missingBuildConfig");
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
