package de.tum.in.www1.artemis.web.rest.dto.lectureunit;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitForLearningPathNodeDetailsDTO(long id, @NonNull String name, @NonNull String type) {

    public static LectureUnitForLearningPathNodeDetailsDTO of(@NonNull LectureUnit lectureUnit) {
        return new LectureUnitForLearningPathNodeDetailsDTO(lectureUnit.getId(), lectureUnit.getName(), lectureUnit.getType());
    }
}
