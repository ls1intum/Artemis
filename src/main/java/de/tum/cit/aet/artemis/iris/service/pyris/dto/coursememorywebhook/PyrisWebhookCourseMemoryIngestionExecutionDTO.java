package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * Body of a Course Memory ingestion webhook request (Artemis &rarr; Pyris,
 * {@code POST /api/v1/webhooks/course-memory/ingest}).
 * <p>
 * Unlike FAQ/lecture ingestion the payload is flat: the memory fields live at the top level next to
 * {@code settings} and {@code initialStages}. Artemis ids are stringified ({@code conversationId},
 * {@code messageId}) and {@code messageId} is the dedup/upsert key on the Pyris side.
 *
 * @param settings        pipeline execution settings (auth token, base url, selection, variant)
 * @param initialStages   optional initial stages, usually empty
 * @param courseId        scopes storage and retrieval; entries are never returned cross-course
 * @param conversationId  stringified id of the originating thread, stored for backlinking
 * @param messageId       stringified stable id of the answer message; dedup/upsert key
 * @param source          origin of the ingestion request, see {@link PyrisCourseMemorySource}
 * @param isPublicChannel must be {@code true}; non-public channels are skipped by Pyris
 * @param thread          full thread ordered oldest&rarr;newest
 * @param verifiedBy      optional identifier of who verified the answer (Trigger A)
 * @param verifiedAt      optional ISO-8601 verification timestamp
 * @param existingAnswer  optional tutor-edited answer used verbatim when {@code source} is
 *                            {@link PyrisCourseMemorySource#IRIS_CORRECTED}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWebhookCourseMemoryIngestionExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages, long courseId, String conversationId,
        String messageId, PyrisCourseMemorySource source, @JsonProperty("isPublicChannel") boolean isPublicChannel, List<PyrisCourseMemoryThreadMessageDTO> thread,
        @Nullable String verifiedBy, @Nullable String verifiedAt, @Nullable String existingAnswer) {
}
