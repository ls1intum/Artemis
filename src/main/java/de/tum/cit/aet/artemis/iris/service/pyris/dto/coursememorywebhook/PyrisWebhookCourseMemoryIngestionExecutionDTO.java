package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;

/**
 * Body of a Course Memory ingestion webhook request (Artemis &rarr; Pyris,
 * {@code POST /api/v1/webhooks/course-memory/ingest}).
 * <p>
 * Unlike FAQ/lecture ingestion the payload is flat: the memory fields live at the top level next to
 * {@code settings}. Artemis ids are stringified and {@code postId} is the dedup/upsert key on the
 * Pyris side, so one resolved thread yields exactly one Course Memory entry however many of its
 * answers resolve it and however often it is corrected.
 *
 * @param settings        pipeline execution settings (auth token, base url, selection, variant)
 * @param courseId        scopes storage and retrieval; entries are never returned cross-course
 * @param conversationId  stringified id of the <em>channel</em> the thread lives in; backlinking only
 * @param postId          stringified id of the thread's root post; the dedup/upsert key
 * @param messageId       stringified id of the answer message that triggered this ingestion; provenance
 *                            only, and deliberately never matched against {@code thread} ids
 * @param version         monotonic per-thread operation version, minted from {@code Post#courseMemoryVersion}
 *                            for this dispatch. Pyris keeps the highest version it has seen per thread and
 *                            drops older operations, so this ingestion can neither resurrect an entry a later
 *                            deletion retracted nor overwrite what a later edit stored, whichever order the
 *                            webhooks arrive or finish in
 * @param source          origin of the ingestion request, see {@link PyrisCourseMemorySource}
 * @param isPublicChannel must be {@code true}; non-public channels are skipped by Pyris
 * @param thread          full thread ordered oldest&rarr;newest
 * @param verifiedBy      optional identifier of who verified the answer (Trigger A)
 * @param verifiedAt      optional ISO-8601 verification timestamp
 * @param existingAnswer  the exact text a tutor signed off on, stored verbatim instead of the extractor's
 *                            paraphrase. Required by Pyris for {@link PyrisCourseMemorySource#IRIS_AUTO} and
 *                            {@link PyrisCourseMemorySource#IRIS_CORRECTED}, absent otherwise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWebhookCourseMemoryIngestionExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, long courseId, String conversationId, String postId, String messageId,
        long version, PyrisCourseMemorySource source, @JsonProperty("isPublicChannel") boolean isPublicChannel, List<PyrisCourseMemoryThreadMessageDTO> thread,
        @Nullable String verifiedBy, @Nullable String verifiedAt, @Nullable String existingAnswer) {
}
