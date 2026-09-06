package de.tum.cit.aet.artemis.deimos.dto;

import de.tum.cit.aet.artemis.core.util.ServedFileUrl;

public record DeimosExerciseScopeInfoDTO(long exerciseId, String exerciseTitle, Long courseId, String courseTitle, String courseIconUrl) {

    /**
     * The icon comes out of the column as a filename, so it is turned into the path the client requests it under. The conversion is idempotent.
     */
    public DeimosExerciseScopeInfoDTO {
        courseIconUrl = ServedFileUrl.courseIcon(courseId, courseIconUrl);
    }
}
