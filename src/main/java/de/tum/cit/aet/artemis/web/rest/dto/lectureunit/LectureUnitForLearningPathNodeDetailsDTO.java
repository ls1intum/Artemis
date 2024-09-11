package de.tum.cit.aet.artemis.web.rest.dto.lectureunit;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitForLearningPathNodeDetailsDTO(long id, @NotNull String name, @NotNull String type) {

    public static LectureUnitForLearningPathNodeDetailsDTO of(@NotNull LectureUnit lectureUnit) {
        return new LectureUnitForLearningPathNodeDetailsDTO(lectureUnit.getId(), lectureUnit.getName(), lectureUnit.getType());
    }
}
