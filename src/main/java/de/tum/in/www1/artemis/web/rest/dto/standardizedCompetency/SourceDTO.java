package de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency;

import de.tum.in.www1.artemis.domain.competency.Source;

/**
 * DTO containing source information
 */
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
