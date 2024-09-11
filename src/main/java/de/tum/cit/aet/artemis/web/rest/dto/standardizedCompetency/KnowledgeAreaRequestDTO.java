package de.tum.cit.aet.artemis.web.rest.dto.standardizedCompetency;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.competency.KnowledgeArea;

/**
 * DTO used to send requests regarding {@link KnowledgeArea} objects. It has no id and only contains the id (not object) of its parent
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KnowledgeAreaRequestDTO(@NotNull @Size(min = 1, max = KnowledgeArea.MAX_TITLE_LENGTH) String title,
        @NotNull @Size(min = 1, max = KnowledgeArea.MAX_SHORT_TITLE_LENGTH) String shortTitle, @Size(max = KnowledgeArea.MAX_DESCRIPTION_LENGTH) String description,
        Long parentId) {
}
