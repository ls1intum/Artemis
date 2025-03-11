package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DropLocation;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DropLocationDTO(Long id, Double posX, Double posY, Double width, Double height, Boolean invalid) {

    public static DropLocationDTO of(DropLocation dropLocation) {
        return new DropLocationDTO(dropLocation.getId(), dropLocation.getPosX(), dropLocation.getPosY(), dropLocation.getWidth(), dropLocation.getHeight(),
                dropLocation.isInvalid());
    }

}
