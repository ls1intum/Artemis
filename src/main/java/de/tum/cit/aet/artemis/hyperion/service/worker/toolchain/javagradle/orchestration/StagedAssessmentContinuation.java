package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import java.time.Instant;
import java.util.List;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.GenerationStage;

/** Server-owned assessment re-entry at SPEC or TESTS, within the existing authoring-attempt and job limits. */
record StagedAssessmentContinuation(Instant startedAt, int reentriesRemaining, String approvedSpecification, String feedback, List<String> specificationFindings,
        List<String> conceptFindings, GenerationStage stage) {

    StagedAssessmentContinuation {
        if (stage == GenerationStage.STATEMENT) {
            throw new IllegalArgumentException("An assessment continuation must review SPEC or verify TESTS before statement authoring");
        }
        specificationFindings = List.copyOf(specificationFindings);
        conceptFindings = List.copyOf(conceptFindings);
    }

    StagedAssessmentContinuation(Instant startedAt, int reentriesRemaining, String approvedSpecification, String feedback, List<String> specificationFindings,
            List<String> conceptFindings) {
        this(startedAt, reentriesRemaining, approvedSpecification, feedback, specificationFindings, conceptFindings, GenerationStage.TESTS);
    }
}
