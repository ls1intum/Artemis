package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * DTO for representing hidden page information.
 */
public record HiddenPageInfoDTO(String slideId,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX") ZonedDateTime date,

        Long exerciseId) {

    /**
     * Check if this hidden page has an associated exercise.
     *
     * @return true if exerciseId is not null, false otherwise
     */
    public boolean hasExercise() {
        return exerciseId != null;
    }
}
