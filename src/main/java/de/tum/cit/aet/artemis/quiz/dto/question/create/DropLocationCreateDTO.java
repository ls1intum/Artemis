package de.tum.cit.aet.artemis.quiz.dto.question.create;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DropLocationCreateDTO(long tempID, double posX, double posY, double width, double height) {
}
