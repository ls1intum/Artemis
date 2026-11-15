package de.tum.cit.aet.artemis.atlas.dto.standardizedCompetency;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.Source;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Citation data without the source entity's competency relationships.
 *
 * @param id     persisted source identifier
 * @param title  citation title, omitted when empty
 * @param author citation author, omitted when absent or empty
 * @param uri    citation URI, omitted when absent or empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SourceDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String title,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String author, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String uri) {

    /**
     * Creates a SourceDTO from the given Source
     *
     * @param source the Source
     * @return the created SourceDTO
     */
    public static SourceDTO of(Source source) {
        return new SourceDTO(source.getId(), source.getTitle(), source.getAuthor(), source.getUri());
    }
}
