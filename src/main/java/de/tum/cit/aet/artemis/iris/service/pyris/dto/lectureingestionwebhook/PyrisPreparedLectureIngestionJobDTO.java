package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

/**
 * A fully prepared lecture ingestion job: the registered job token plus the execution payload Pyris
 * needs to run it. Preparation and delivery are separate steps so both dispatch transports can share
 * the same assembly: the legacy push path POSTs the payload to Pyris, while the pull path hands the
 * identical payload to a Pyris worker as its claim response.
 *
 * @param jobToken     the registered Pyris job token authenticating this run's status callbacks
 * @param executionDTO the execution payload, byte-identical to the legacy webhook body
 */
public record PyrisPreparedLectureIngestionJobDTO(String jobToken, PyrisWebhookLectureIngestionExecutionDTO executionDTO) {
}
