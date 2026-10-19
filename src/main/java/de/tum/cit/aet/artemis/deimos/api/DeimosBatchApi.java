package de.tum.cit.aet.artemis.deimos.api;

import org.springframework.http.ResponseEntity;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchRequestDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchTriggerResponseDTO;

public interface DeimosBatchApi extends AbstractApi {

    ResponseEntity<DeimosBatchTriggerResponseDTO> triggerCourseBatch(long courseId, DeimosBatchRequestDTO request);

    ResponseEntity<DeimosBatchTriggerResponseDTO> triggerExerciseBatch(long exerciseId, DeimosBatchRequestDTO request);
}
