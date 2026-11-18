package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RenderedProblemStatementDTO(@NonNull String html, @NonNull String contentHash, @NonNull String rendererVersion, @Nullable String interactiveScript)
        implements Serializable {

    public RenderedProblemStatementDTO {
        // @JsonInclude(NON_EMPTY) omits empty strings during serialization (e.g. an empty html for blank
        // markdown). Normalize them back on deserialization so the @NonNull contract holds and consumers
        // never observe null for these always-present output fields.
        html = html == null ? "" : html;
        contentHash = contentHash == null ? "" : contentHash;
        rendererVersion = rendererVersion == null ? "" : rendererVersion;
    }
}
