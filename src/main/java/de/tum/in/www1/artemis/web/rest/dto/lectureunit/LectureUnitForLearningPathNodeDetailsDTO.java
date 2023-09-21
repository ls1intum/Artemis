package de.tum.in.www1.artemis.web.rest.dto.lectureunit;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

public record LectureUnitForLearningPathNodeDetailsDTO(long id, @NotNull String name, @NotNull String type) {

    public static LectureUnitForLearningPathNodeDetailsDTO of(@NotNull LectureUnit lectureUnit) {
        return new LectureUnitForLearningPathNodeDetailsDTO(lectureUnit.getId(), lectureUnit.getName(), lectureUnit.getType());
    }
}
