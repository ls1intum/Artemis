package de.tum.cit.aet.artemis.web.rest.dto.standardizedCompetency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.competency.Source;

/**
 * DTO containing source information
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SourceDTO(long id, String title, String author, String uri) {

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
