package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The jobs handed to a pulling Pyris worker. Each entry is byte-identical to the body of the legacy
 * push webhook, so the worker executes both transports through the same pipeline code.
 *
 * @param jobs the claimed, activated ingestion jobs
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWorkerClaimResponseDTO(List<PyrisWebhookLectureIngestionExecutionDTO> jobs) {
}
