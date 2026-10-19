package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A fully prepared lecture ingestion job: the registered job token plus the execution payload Pyris
 * needs to run it. Preparation and delivery are separate steps so both dispatch transports can share
 * the same assembly: the legacy push path POSTs the payload to Pyris, while the pull path hands the
 * identical payload to a Pyris worker as its claim response.
 *
 * @param jobToken     the registered Pyris job token authenticating this run's status callbacks
 * @param executionDTO the execution payload, byte-identical to the legacy webhook body
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisPreparedLectureIngestionJobDTO(String jobToken, PyrisWebhookLectureIngestionExecutionDTO executionDTO) {

    /**
     * The fingerprint the payload actually carries, which Iris stamps into the index. The claim activates with this value, so the fingerprint Artemis
     * confirms on success is the one the index holds, also when the video was left out.
     *
     * @return the content fingerprint sent to Iris
     */
    public String contentFingerprint() {
        return executionDTO.pyrisLectureUnit().contentFingerprint();
    }

    /**
     * Whether the payload carries a video for Iris to transcribe.
     *
     * @return true if a video link is sent
     */
    public boolean includesVideo() {
        String videoLink = executionDTO.pyrisLectureUnit().videoLink();
        return videoLink != null && !videoLink.isBlank();
    }
}
