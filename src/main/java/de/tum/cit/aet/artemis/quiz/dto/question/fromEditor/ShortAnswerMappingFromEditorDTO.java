package de.tum.cit.aet.artemis.quiz.dto.question.fromEditor;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

/**
 * DTO for short answer mappings in the editor context.
 * Uses temporary IDs to reference solutions and spots.
 * For persisted entities, uses real IDs as fallback when tempID is null.
 *
 * @param id             the ID of the mapping, null for new mappings
 * @param solutionTempId the temporary ID of the associated solution (can be null for persisted entities, will use real ID)
 * @param spotTempId     the temporary ID of the associated spot (can be null for persisted entities, will use real ID)
 */
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
        // Use real ID as fallback for tempID when dealing with persisted entities
        Long solutionEffectiveId = mapping.getSolution().getTempID() != null ? mapping.getSolution().getTempID() : mapping.getSolution().getId();
        Long spotEffectiveId = mapping.getSpot().getTempID() != null ? mapping.getSpot().getTempID() : mapping.getSpot().getId();
        return new ShortAnswerMappingFromEditorDTO(mapping.getId(), solutionEffectiveId, spotEffectiveId);
    }

    /**
     * Creates a new ShortAnswerMapping domain object from this DTO.
     * The mapping contains temporary ShortAnswerSolution and ShortAnswerSpot objects that need to be resolved later.
     *
     * @return a new ShortAnswerMapping domain object
     */
    public ShortAnswerMapping toDomainObject() {
        ShortAnswerMapping mapping = new ShortAnswerMapping();
        ShortAnswerSolution solution = new ShortAnswerSolution();
        ShortAnswerSpot spot = new ShortAnswerSpot();
        solution.setTempID(solutionTempId);
        spot.setTempID(spotTempId);
        mapping.setSolution(solution);
        mapping.setSpot(spot);
        return mapping;
    }
}
