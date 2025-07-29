package de.tum.cit.aet.artemis.hyperion.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionReviewAndRefineRestService;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * REST controller for programming exercise review and enhancement using Hyperion service.
 */
@RestController
@Lazy
@Profile(PROFILE_HYPERION)
@RequestMapping("api/hyperion/programming/")
public class HyperionReviewAndRefineResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionReviewAndRefineResource.class);

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final HyperionReviewAndRefineRestService reviewAndRefineService;

    public HyperionReviewAndRefineResource(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            HyperionReviewAndRefineRestService reviewAndRefineService) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.reviewAndRefineService = reviewAndRefineService;
    }

    /**
     * Analyzes a programming exercise for consistency issues between problem statement,
     * template code, solution code, and test cases using the Hyperion service.
     *
     * @param exerciseId the ID of the programming exercise to analyze
     * @return HTTP 200 with consistency analysis results, or appropriate error status
     */
    @Operation(summary = "Check exercise consistency", description = "Analyzes a programming exercise for consistency issues between problem statement, template code, solution code, and test cases")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Consistency check completed successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConsistencyCheckResponseDTO.class))),
            @ApiResponse(responseCode = "503", description = "Hyperion service unavailable"), @ApiResponse(responseCode = "500", description = "Internal server error") })
    @EnforceAtLeastInstructor
    @PostMapping("exercises/{exerciseId}/consistency-check")
    public ResponseEntity<ConsistencyCheckResponseDTO> checkExerciseConsistency(
            @Parameter(description = "ID of the programming exercise to analyze", required = true) @PathVariable Long exerciseId) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);

        log.info("Performing consistency check for exercise {} by user {}", exerciseId, user.getLogin());

        try {
            ConsistencyCheckResponseDTO result = reviewAndRefineService.checkConsistency(user, programmingExercise);
            log.info("Consistency check completed successfully for exercise {}", exerciseId);
            return ResponseEntity.ok(result);
        }
        catch (NetworkingException e) {
            log.error("Consistency check failed for exercise {}: {}", exerciseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        catch (Exception e) {
            log.error("Consistency check failed for exercise {}: {}", exerciseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Rewrites and improves a problem statement using the Hyperion service.
     *
     * @param exerciseId the ID of the programming exercise
     * @param requestDTO the request containing the problem statement text to be improved
     * @return HTTP 200 with improved problem statement text, or appropriate error status
     */
    @Operation(summary = "Rewrite problem statement", description = "Rewrites and improves a problem statement using AI assistance")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Problem statement rewritten successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemStatementRewriteResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body"), @ApiResponse(responseCode = "503", description = "Hyperion service unavailable"),
            @ApiResponse(responseCode = "500", description = "Internal server error") })
    @EnforceAtLeastInstructor
    @PostMapping("exercises/{exerciseId}/problem-statement-rewrite")
    public ResponseEntity<ProblemStatementRewriteResponseDTO> rewriteProblemStatement(
            @Parameter(description = "ID of the programming exercise", required = true) @PathVariable Long exerciseId,
            @Parameter(description = "Request containing the problem statement to rewrite", required = true) @RequestBody ProblemStatementRewriteRequestDTO requestDTO) {

        if (requestDTO.problemStatementText() == null || requestDTO.problemStatementText().trim().isEmpty()) {
            log.warn("Problem statement rewrite requested with empty text for exercise {}", exerciseId);
            return ResponseEntity.badRequest().build();
        }

        var user = userRepository.getUserWithGroupsAndAuthorities();
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);

        log.info("Rewriting problem statement for exercise {} by user {}", exerciseId, user.getLogin());

        try {
            ProblemStatementRewriteResponseDTO result = reviewAndRefineService.rewriteProblemStatement(user, programmingExercise, requestDTO.problemStatementText());
            log.info("Problem statement rewrite completed successfully for exercise {}", exerciseId);
            return ResponseEntity.ok(result);
        }
        catch (NetworkingException e) {
            log.error("Problem statement rewrite failed for exercise {}: {}", exerciseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        catch (Exception e) {
            log.error("Problem statement rewrite failed for exercise {}: {}", exerciseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
