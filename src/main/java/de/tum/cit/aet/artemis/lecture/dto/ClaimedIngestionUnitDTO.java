package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;

/**
 * One lecture unit claimed by a pulling Pyris worker, described by scalars only so the iris module
 * can consume it across the module boundary. The iris side re-fetches the unit entity by id, builds
 * the execution payload, and then activates the claim with the registered job token.
 *
 * @param lectureUnitId      id of the claimed attachment video unit
 * @param contentFingerprint fingerprint of the unit's source content at claim time
 * @param forceReingest      true when this claim is a quality re-ingestion bypassing Iris's skip checks
 * @param targetPhase        the in-flight phase the run enters on activation (TRANSCRIBING or INGESTING)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ClaimedIngestionUnitDTO(long lectureUnitId, String contentFingerprint, boolean forceReingest, ProcessingPhase targetPhase) {
}
