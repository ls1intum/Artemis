package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * Status update sent by Pyris while ingesting a Course Memory entry (Pyris &rarr; Artemis).
 * Mirrors the FAQ/lecture ingestion status shape. The entry is stored on Pyris regardless of whether
 * Artemis processes the callback, so Artemis only needs to track job lifecycle.
 *
 * @param result    optional textual result
 * @param stages    progress stages
 * @param id        optional identifier echoed by Pyris
 * @param errorCode optional error code on failure
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseMemoryIngestionStatusUpdateDTO(@Nullable String result, List<PyrisStageDTO> stages, @Nullable String id,
        @Nullable @JsonProperty("error_code") String errorCode) {
}
