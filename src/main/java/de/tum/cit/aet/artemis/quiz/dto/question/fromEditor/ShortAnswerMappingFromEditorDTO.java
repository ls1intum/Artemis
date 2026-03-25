package de.tum.cit.aet.artemis.quiz.dto.question.fromEditor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;

/**
 * DTO for short answer mappings in the editor context.
 * Uses temporary IDs to reference solutions and spots.
 * For persisted entities, uses real IDs as fallback when tempID is null.
 *
 * @param id             the ID of the mapping, null for new mappings
 * @param solutionTempId the temporary ID of the associated solution (can be null for persisted entities, will use real ID)
 * @param spotTempId     the temporary ID of the associated spot (can be null for persisted entities, will use real ID)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerMappingFromEditorDTO(Long id, Long solutionTempId, Long spotTempId) {

    /**
     * Creates a ShortAnswerMappingFromEditorDTO from the given ShortAnswerMapping domain object.
     * For persisted entities, uses real IDs as tempIDs when tempID is null.
     *
     * @param mapping the mapping to convert
     * @return the corresponding DTO
     */
    public static ShortAnswerMappingFromEditorDTO of(ShortAnswerMapping mapping) {
        if (mapping.getSolution() == null || mapping.getSpot() == null) {
            throw new IllegalArgumentException("ShortAnswerMapping must have both solution and spot set");
        }
        return new ShortAnswerMappingFromEditorDTO(mapping.getId(), mapping.getSolution().getId(), mapping.getSpot().getId());
    }

    /**
     * Creates a new ShortAnswerMapping domain object from this DTO.
     * The mapping contains temporary ShortAnswerSolution and ShortAnswerSpot objects that need to be resolved later.
     *
     * @return a new ShortAnswerMapping domain object
     */
    public ShortAnswerMapping toDomainObject() {
        ShortAnswerMapping mapping = new ShortAnswerMapping();
        return mapping;
    }
}
