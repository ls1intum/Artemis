package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerMappingCreateDTO(@NotNull Long solutionTempId, @NotNull Long spotTempId) {

    /**
     * Converts this DTO to a {@link ShortAnswerMapping} domain object.
     * <p>
     * Creates temporary {@link ShortAnswerSolution} and {@link ShortAnswerSpot} objects populated
     * with the provided tempIDs and associates them with the mapping. This method is used to
     * initialize mappings prior to resolution with actual domain objects in the question.
     *
     * @return the {@link ShortAnswerMapping} domain object with temporary solution and spot references
     */
    public ShortAnswerMapping toDomainObject() {
        ShortAnswerMapping shortAnswerMapping = new ShortAnswerMapping();
        ShortAnswerSolution shortAnswerSolution = new ShortAnswerSolution();
        ShortAnswerSpot shortAnswerSpot = new ShortAnswerSpot();
        shortAnswerSolution.setTempID(solutionTempId);
        shortAnswerSpot.setTempID(spotTempId);
        shortAnswerMapping.setSolution(shortAnswerSolution);
        shortAnswerMapping.setSpot(shortAnswerSpot);
        return shortAnswerMapping;
    }

    /**
     * Creates a {@link ShortAnswerMappingCreateDTO} from the given {@link ShortAnswerMapping} domain object.
     * <p>
     * Maps the temporary IDs of the associated {@link ShortAnswerSolution} and {@link ShortAnswerSpot}
     * to the corresponding DTO fields.
     *
     * @param mapping the {@link ShortAnswerMapping} domain object to convert
     * @return the {@link ShortAnswerMappingCreateDTO} with temp IDs set from the domain object
     */
    public static ShortAnswerMappingCreateDTO of(ShortAnswerMapping mapping) {
        return new ShortAnswerMappingCreateDTO(mapping.getSolution().getTempID(), mapping.getSpot().getTempID());
    }
}
