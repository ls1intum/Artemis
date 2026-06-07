package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The structured outcome of the differential verification, mirrored from {@code VerificationResult} so the client can show which gates passed without parsing prose.
 * <p>
 * {@link Serializable} because it is carried inside {@link ExerciseGenerationEventDTO}, which is retained in a distributed Hazelcast map for reconnect/replay.
 *
 * @param accepted       whether the exercise was accepted
 * @param solutionPassed whether the solution passed all its tests
 * @param templateFailed whether the template compiled but (correctly) failed the tests
 * @param testCount      the number of tests discovered
 * @param reasons        human-readable explanations of any failed gate (empty when accepted)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationVerdictDTO(boolean accepted, boolean solutionPassed, boolean templateFailed, int testCount, List<String> reasons) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
