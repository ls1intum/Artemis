package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerMappingCreateDTO(@NotNull Long solutionTempId, @NotNull Long spotTempId) {

    /**
     * Converts this DTO to a {@link ShortAnswerMapping} domain object.
     *
     * @return a bare {@link ShortAnswerMapping} domain object (mapping resolution happens at question level)
     */
    public ShortAnswerMapping toDomainObject() {
        ShortAnswerMapping shortAnswerMapping = new ShortAnswerMapping();
        return shortAnswerMapping;
    }

    /**
     * Creates a {@link ShortAnswerMappingCreateDTO} from the given {@link ShortAnswerMapping} domain object.
     *
     * @param mapping the {@link ShortAnswerMapping} domain object to convert
     * @return the {@link ShortAnswerMappingCreateDTO} with IDs set from the domain object
     */
    public static ShortAnswerMappingCreateDTO of(ShortAnswerMapping mapping) {
        return new ShortAnswerMappingCreateDTO(mapping.getSolution().getId(), mapping.getSpot().getId());
    }
}
