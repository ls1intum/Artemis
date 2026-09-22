package de.tum.cit.aet.artemis.hyperion.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.AuthoringRunPageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history.GenerationHistoryService;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/** Canonical run lookup and cross-browser discovery, scoped to the owner and their current course permissions. */
@RestController
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
@RequestMapping("api/hyperion/")
@FeatureUsage("authoring-assistance/exercise-generation")
public class HyperionAuthoringRunResource {

    private final GenerationHistoryService history;

    private final UserRepository users;

    public HyperionAuthoringRunResource(GenerationHistoryService history, UserRepository users) {
        this.history = history;
        this.users = users;
    }

    /**
     * Lists the authenticated owner's currently authorized runs, newest first, at most 50 per page.
     *
     * @param beforeId exclusive pagination cursor
     * @return bounded history page without sensitive content
     */
    @GetMapping("authoring-runs")
    @EnforceAtLeastEditor
    public ResponseEntity<AuthoringRunPageDTO> getAuthoringRuns(@RequestParam(required = false) @Positive Long beforeId) {
        return ResponseEntity.ok(history.history(users.getUserWithCourseRolesAndAuthorities(), beforeId));
    }

    /**
     * Rechecks current access to retained history in one bounded, read-only batch. Omitted ids reveal neither
     * another owner's runs nor destinations that the caller can no longer edit.
     *
     * @param jobIds between one and 500 canonical run identities
     * @return the subset currently accessible to their owner
     */
    @PostMapping("authoring-runs/access")
    @EnforceAtLeastEditor
    @ApiResponse(responseCode = "200", description = "Currently authorized run identities", content = @Content(array = @ArraySchema(maxItems = 500, schema = @Schema(type = "string", maxLength = 128))))
    public ResponseEntity<List<String>> checkAuthoringRunAccess(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(array = @ArraySchema(minItems = 1, maxItems = 500, schema = @Schema(type = "string", minLength = 1, maxLength = 128)))) @RequestBody @Valid @NotEmpty @Size(max = 500) List<@NotBlank @Size(max = 128) String> jobIds) {
        return ResponseEntity.ok(history.authorizedRunIds(users.getUserWithCourseRolesAndAuthorities(), jobIds));
    }

    /**
     * Reads one immutable run without substituting a newer run on its destination.
     *
     * @param exerciseId authorized destination
     * @param runId      canonical run identifier
     * @return retained replay or the durable outcome when progress has expired
     */
    @GetMapping("programming-exercises/{exerciseId}/generation/runs/{runId}")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<ExerciseGenerationStatusDTO> getAuthoringRunStatus(@PathVariable long exerciseId, @PathVariable String runId) {
        return ResponseEntity.ok(history.status(exerciseId, runId, users.getUserWithCourseRolesAndAuthorities()));
    }
}
