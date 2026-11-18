package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScoreDistributionDTO(long amount, double score) {

    public ScoreDistributionDTO(Long amount, Double score) {
        this(amount != null ? amount : 0, score != null ? score : 0.0);
    }
}
