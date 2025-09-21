package de.tum.cit.aet.artemis.quiz.dto.question.create;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerMappingCreateDTO(long solutionTempId, long spotTempId) {
}
