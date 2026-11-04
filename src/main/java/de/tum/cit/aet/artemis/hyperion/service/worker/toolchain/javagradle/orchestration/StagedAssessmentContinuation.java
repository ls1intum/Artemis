package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import java.time.Instant;
import java.util.List;

/** Server-owned continuation of an approved redesign's unfinished executable stage, within the existing authoring-attempt and job limits. */
record StagedAssessmentContinuation(Instant startedAt, int reentriesRemaining, String approvedSpecification, String feedback, List<String> specificationFindings,
        List<String> conceptFindings) {

    StagedAssessmentContinuation {
        specificationFindings = List.copyOf(specificationFindings);
        conceptFindings = List.copyOf(conceptFindings);
    }
}
