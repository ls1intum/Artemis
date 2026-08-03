package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;

/**
 * Status update sent by Pyris while ingesting a Course Memory entry (Pyris &rarr; Artemis).
 * Mirrors the FAQ/lecture ingestion status shape. The entry is stored on Pyris regardless of whether
 * Artemis processes the callback, so Artemis only needs to track job lifecycle.
 *
 * @param result   optional textual result
 * @param runState lifecycle state of the ingestion run
 * @param error    optional error details on failure
 * @param id       optional identifier echoed by Pyris
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseMemoryIngestionStatusUpdateDTO(@Nullable String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable Long id) {
}
