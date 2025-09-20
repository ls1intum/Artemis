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

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationResultDTO;
import de.tum.cit.aet.artemis.hyperion.service.codegeneration.HyperionCodeGenerationExecutionService;
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

    private static final String ENTITY_NAME = "codeGeneration";

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final HyperionCodeGenerationExecutionService codeGenerationExecutionService;

    public HyperionCodeGenerationResource(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            HyperionCodeGenerationExecutionService codeGenerationExecutionService) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.codeGenerationExecutionService = codeGenerationExecutionService;
    }

    /**
     * POST exercises/{exerciseId}/generate-code: Generate code for a programming exercise.
     * Uses AI-powered iterative approach to generate, compile, and improve code based on build feedback.
     * Supports generation for SOLUTION, TEMPLATE, and TESTS repositories.
     *
     * @param exerciseId the ID of the programming exercise to generate code for
     * @param request    the request containing repository type specification
     * @return ResponseEntity with status 200 (OK) and the generation result, or error status
     */
    @PostMapping("exercises/{exerciseId}/generate-code")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<CodeGenerationResultDTO> generateCode(@PathVariable long exerciseId, @Valid @RequestBody CodeGenerationRequestDTO request) {
        log.debug("REST request to generate code for programming exercise [{}] with repository type [{}]", exerciseId, request.repositoryType());

        validateGenerationRequest(exerciseId, request);

        ProgrammingExercise exercise = loadProgrammingExercise(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Result generationResult = executeCodeGeneration(exercise, user, request.repositoryType());
        CodeGenerationResultDTO response = buildGenerationResponse(generationResult, request.repositoryType());

        log.info("Code generation completed for exercise [{}] with repository type [{}]: success=[{}]", exerciseId, request.repositoryType(), response.success());

        return ResponseEntity.ok(response);
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
     * Executes the code generation process.
     *
     * @param exercise       the programming exercise
     * @param user           the requesting user
     * @param repositoryType the target repository type
     * @return the generation result, or null if generation failed
     */
    private Result executeCodeGeneration(ProgrammingExercise exercise, User user, RepositoryType repositoryType) {
        try {
            return codeGenerationExecutionService.generateAndCompileCode(exercise, user, repositoryType);
        }
        catch (Exception e) {
            log.error("Code generation failed for exercise [{}] with repository type [{}]", exercise.getId(), repositoryType, e);
            return null;
        }
    }

    /**
     * Builds the response DTO from the generation result.
     *
     * @param result         the generation result (may be null if failed)
     * @param repositoryType the repository type that was processed
     * @return the response DTO
     */
    private CodeGenerationResultDTO buildGenerationResponse(Result result, RepositoryType repositoryType) {
        if (result == null) {
            return new CodeGenerationResultDTO(false, "Code generation failed after maximum attempts. Please check the exercise configuration and try again.", 3);
        }

        boolean isSuccessful = result.isSuccessful();
        String message = buildSuccessMessage(isSuccessful, repositoryType);

        int attempts = isSuccessful ? 1 : 3;

        return new CodeGenerationResultDTO(isSuccessful, message, attempts);
    }

    /**
     * Builds an appropriate success/failure message.
     *
     * @param isSuccessful   whether the generation was successful
     * @param repositoryType the repository type that was processed
     * @return the message string
     */
    private String buildSuccessMessage(boolean isSuccessful, RepositoryType repositoryType) {
        if (isSuccessful) {
            return switch (repositoryType) {
                case SOLUTION -> "Solution code generated successfully and compiles without errors.";
                case TEMPLATE -> "Template code generated successfully and compiles without errors.";
                case TESTS -> "Test code generated successfully and compiles without errors.";
                default -> "Code generated successfully and compiles without errors.";
            };
        }
        else {
            return switch (repositoryType) {
                case SOLUTION -> "Solution code generation failed. The generated code contains compilation errors that could not be resolved.";
                case TEMPLATE -> "Template code generation failed. The generated code contains compilation errors that could not be resolved.";
                case TESTS -> "Test code generation failed. The generated code contains compilation errors that could not be resolved.";
                default -> "Code generation failed. The generated code contains compilation errors that could not be resolved.";
            };
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
