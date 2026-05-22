package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;

/**
 * WebSocket message sent from Artemis to the Angular client for global search Iris answer status updates.
 *
 * <p>
 * There are two kinds of messages:
 * <ul>
 * <li>{@code isThinking=true}: Pyris classified the query as a real question; LLM is running.</li>
 * <li>{@code isThinking=false}: Pipeline finished. Show the {@code answer} card if non-null; hide otherwise.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IrisGlobalSearchAnswerWebsocketDTO(@NotBlank String runId, boolean isThinking, @Nullable String answer, @Nullable List<PyrisLectureSearchResultDTO> sources) {
}
