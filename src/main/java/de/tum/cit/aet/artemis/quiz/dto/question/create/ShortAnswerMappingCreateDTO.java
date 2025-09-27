package de.tum.cit.aet.artemis.quiz.dto.question.create;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerMappingCreateDTO(long solutionTempId, long spotTempId) {

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
}
