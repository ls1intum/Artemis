package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The structured outcome of the differential verification, mirrored from {@code VerificationResult} so the client can show which gates passed without parsing prose.
 * <p>
 * {@link Serializable} because it is carried inside {@link ExerciseGenerationEventDTO}, which is retained in a distributed Hazelcast map for reconnect/replay.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "The structured outcome of the differential verification, so the client can show which gates passed without parsing prose")
public record ExerciseGenerationVerdictDTO(
        @Schema(description = "Whether the exercise passed mechanical verification", requiredMode = Schema.RequiredMode.REQUIRED) boolean mechanicallyVerified,
        @Schema(description = "Whether the solution passed all its tests", requiredMode = Schema.RequiredMode.REQUIRED) boolean solutionPassed,
        @Schema(description = "Whether the template compiled but correctly failed the tests", requiredMode = Schema.RequiredMode.REQUIRED) boolean templateFailed,
        @Schema(description = "The number of tests discovered", requiredMode = Schema.RequiredMode.REQUIRED) int testCount,
        @JsonInclude @Schema(description = "Human-readable explanations of any failed gate (empty when mechanically verified)", requiredMode = Schema.RequiredMode.REQUIRED) List<String> reasons)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
