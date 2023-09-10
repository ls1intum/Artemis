package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.Rating;

public record RatingDTO(Integer rating, DomainObjectIdDTO result) {

    public RatingDTO(Rating rating) {
        this(rating.getRating(), new DomainObjectIdDTO(rating.getResult()));
    }
}
