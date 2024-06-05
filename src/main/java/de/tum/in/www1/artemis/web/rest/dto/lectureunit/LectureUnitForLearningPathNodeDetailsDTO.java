package de.tum.in.www1.artemis.web.rest.dto.lectureunit;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitForLearningPathNodeDetailsDTO(long id, @NotNull String name, @NotNull String type) {

    public static LectureUnitForLearningPathNodeDetailsDTO of(@NotNull LectureUnit lectureUnit) {
        return new LectureUnitForLearningPathNodeDetailsDTO(lectureUnit.getId(), lectureUnit.getName(), lectureUnit.getType());
    }
}
