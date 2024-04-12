package de.tum.in.www1.artemis.web.rest.dto.lectureunit;

import jakarta.annotation.Nonnull;

import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

public record LectureUnitForLearningPathNodeDetailsDTO(long id, @Nonnull String name, @Nonnull String type) {

    public static LectureUnitForLearningPathNodeDetailsDTO of(@Nonnull LectureUnit lectureUnit) {
        return new LectureUnitForLearningPathNodeDetailsDTO(lectureUnit.getId(), lectureUnit.getName(), lectureUnit.getType());
    }
}
