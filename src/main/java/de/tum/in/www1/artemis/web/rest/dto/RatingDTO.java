package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Rating;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RatingDTO(Integer rating, DomainObjectIdDTO result) {

    public RatingDTO(Rating rating) {
        this(rating.getRating(), new DomainObjectIdDTO(rating.getResult()));
    }
}
