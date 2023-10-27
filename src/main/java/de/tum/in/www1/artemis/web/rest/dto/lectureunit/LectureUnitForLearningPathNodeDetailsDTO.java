package de.tum.in.www1.artemis.web.rest.dto.lectureunit;

import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import jakarta.validation.constraints.NotNull;

public record LectureUnitForLearningPathNodeDetailsDTO(long id, @NotNull String name, @NotNull String type) {

    public static LectureUnitForLearningPathNodeDetailsDTO of(@NotNull LectureUnit lectureUnit) {
        return new LectureUnitForLearningPathNodeDetailsDTO(lectureUnit.getId(), lectureUnit.getName(), lectureUnit.getType());
    }
}
